package com.debug.kill.server.config;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 通用化 Rabbitmq 配置     通用的配置 直接拿去用就可以了
 */
@Configuration
public class RabbitmqConfig {

    private final static Logger log = LoggerFactory.getLogger(RabbitmqConfig.class);

    @Autowired
    private Environment env;

    @Autowired         //建立连接
    private CachingConnectionFactory connectionFactory;

    @Autowired     //配置消费者实例
    private SimpleRabbitListenerContainerFactoryConfigurer factoryConfigurer;

    /**
     * 单一消费者
     * @return
     */
    @Bean(name = "singleListenerContainer")
    public SimpleRabbitListenerContainerFactory listenerContainer(){
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(1);    //单一的话就只有一个
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setTxSize(1);
        return factory;
    }

    /**
     * 多个消费者
     * @return
     */
    @Bean(name = "multiListenerContainer")
    public SimpleRabbitListenerContainerFactory multiListenerContainer(){
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factoryConfigurer.configure(factory,connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        //确认消费模式-NONE
        factory.setAcknowledgeMode(AcknowledgeMode.NONE);
        factory.setConcurrentConsumers(env.getProperty("spring.rabbitmq.listener.simple.concurrency",int.class));
        factory.setMaxConcurrentConsumers(env.getProperty("spring.rabbitmq.listener.simple.max-concurrency",int.class));
        factory.setPrefetchCount(env.getProperty("spring.rabbitmq.listener.simple.prefetch",int.class));
        return factory;
    }

    @Bean    //发送消息的核心组件
    public RabbitTemplate rabbitTemplate(){
        connectionFactory.setPublisherConfirms(true);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                log.info("消息发送成功:correlationData({}),ack({}),cause({})",correlationData,ack,cause);
            }
        });
        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            @Override
            public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
                log.warn("消息丢失:exchange({}),route({}),replyCode({}),replyText({}),message:{}",exchange,routingKey,replyCode,replyText,message);
            }
        });
        return rabbitTemplate;
    }


    //构建异步发送邮箱通知的消息模型     成功发送邮件的模型----队列  交换机
    @Bean
    public Queue successEmailQueue(){
        return new Queue(env.getProperty("mq.kill.item.success.email.queue"),true);  //设置持久化
    }

    @Bean
    public TopicExchange successEmailExchange(){
        return new TopicExchange(env.getProperty("mq.kill.item.success.email.exchange"),true,false);   //设置持久化  不让自动删除
    }

    @Bean
    public Binding successEmailBinding(){    //最后面的路由哪来的   交换机+路由绑定到这个队列中来
        return BindingBuilder.bind(successEmailQueue()).to(successEmailExchange()).with(env.getProperty("mq.kill.item.success.email.routing.key"));
    }


    //构建秒杀成功之后-订单超时未支付的死信队列消息模型                         死信路由和死信交换机组成
    @Bean
    public Queue successKillDeadQueue(){
        Map<String, Object> argsMap= Maps.newHashMap();
        //下面这两个参数需要自己去官网配置上看     死信队列的ttl在别的地方动态设置而不是在这里写死
        argsMap.put("x-dead-letter-exchange",env.getProperty("mq.kill.item.success.kill.dead.exchange"));
        argsMap.put("x-dead-letter-routing-key",env.getProperty("mq.kill.item.success.kill.dead.routing.key"));
        return new Queue(env.getProperty("mq.kill.item.success.kill.dead.queue"),true,false,false,argsMap);
    }

    //基本交换机
    @Bean
    public TopicExchange successKillDeadProdExchange(){
        return new TopicExchange(env.getProperty("mq.kill.item.success.kill.dead.prod.exchange"),true,false);
    }

    //创建基本交换机+基本路由 -> 死信队列 的绑定
    @Bean
    public Binding successKillDeadProdBinding(){
        return BindingBuilder.bind(successKillDeadQueue()).to(successKillDeadProdExchange()).with(env.getProperty("mq.kill.item.success.kill.dead.prod.routing.key"));
    }

    //真正的队列
    @Bean
    public Queue successKillRealQueue(){
        return new Queue(env.getProperty("mq.kill.item.success.kill.dead.real.queue"),true);
    }

    //死信交换机
    @Bean
    public TopicExchange successKillDeadExchange(){
        return new TopicExchange(env.getProperty("mq.kill.item.success.kill.dead.exchange"),true,false);
    }

    //死信交换机+死信路由->真正队列 的绑定
    @Bean
    public Binding successKillDeadBinding(){
        return BindingBuilder.bind(successKillRealQueue()).to(successKillDeadExchange()).with(env.getProperty("mq.kill.item.success.kill.dead.routing.key"));
    }


    //TODO:RabbitMQ限流专用

    @Bean
    public Queue executeLimitQueue(){
        Map<String, Object> argsMap=Maps.newHashMap();
        //限制channel中队列同一时刻通过的消息数量
        argsMap.put("x-max-length", env.getProperty("spring.rabbitmq.listener.simple.prefetch",Integer.class));
        return new Queue(env.getProperty("mq.kill.item.execute.limit.queue.name"),true,false,false,argsMap);
    }

    @Bean
    public TopicExchange executeLimitExchange(){
        return new TopicExchange(env.getProperty("mq.kill.item.execute.limit.queue.exchange"),true,false);
    }

    @Bean
    public Binding executeLimitBinding(){
        return BindingBuilder.bind(executeLimitQueue()).to(executeLimitExchange()).with(env.getProperty("mq.kill.item.execute.limit.queue.routing.key"));
    }
}
