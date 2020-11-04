package com.debug.kill.server.service;/**
 * Created by Administrator on 2019/6/21.
 */

import com.debug.kill.model.dto.KillSuccessUserInfo;
import com.debug.kill.model.mapper.ItemKillSuccessMapper;
import com.debug.kill.server.dto.KillDto;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.AbstractJavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ通用的消息发送服务
 * @Author:debug (SteadyJack)
 * @Date: 2019/6/21 21:47
 **/
@Service
public class RabbitSenderService {

    public static final Logger log= LoggerFactory.getLogger(RabbitSenderService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private Environment env;

    @Autowired
    private ItemKillSuccessMapper itemKillSuccessMapper;


    /**
     * 秒杀成功异步发送邮件通知消息
     */
    public void sendKillSuccessEmailMsg(String orderNo){    //传递订单编号
        log.info("秒杀成功异步发送邮件通知消息-准备发送消息：{}",orderNo);

        try {
            if (StringUtils.isNotBlank(orderNo)){
                KillSuccessUserInfo info=itemKillSuccessMapper.selectByCode(orderNo);
                if (info!=null){
                    //TODO:rabbitmq发送消息的逻辑
                    rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
                    rabbitTemplate.setExchange(env.getProperty("mq.kill.item.success.email.exchange"));    //这个路由应该是自己在rabbitMQ中配的
                    rabbitTemplate.setRoutingKey(env.getProperty("mq.kill.item.success.email.routing.key"));

                    //TODO：将info充当消息发送至队列
                    rabbitTemplate.convertAndSend(info, new MessagePostProcessor() {
                        @Override
                        public Message postProcessMessage(Message message) throws AmqpException {
                            MessageProperties messageProperties=message.getMessageProperties();
                            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);  //保证消息的可靠性 持久化
                            messageProperties.setHeader(AbstractJavaTypeMapper.DEFAULT_CONTENT_CLASSID_FIELD_NAME,KillSuccessUserInfo.class);
                            return message;
                        }
                    });
                }
            }
        }catch (Exception e){
            log.error("秒杀成功异步发送邮件通知消息-发生异常，消息为：{}",orderNo,e.fillInStackTrace());
        }
    }


    /**
     * 秒杀成功后生成抢购订单-发送信息入死信队列，等待着一定时间失效超时未支付的订单
     * @param orderCode
     */
    public void sendKillSuccessOrderExpireMsg(final String orderCode){
        try {
            if (StringUtils.isNotBlank(orderCode)){
                //对象信息充当消息进行发送
                KillSuccessUserInfo info=itemKillSuccessMapper.selectByCode(orderCode);
                if (info!=null){
                    rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
                    //此时是基本交换机 和基本路由
                    rabbitTemplate.setExchange(env.getProperty("mq.kill.item.success.kill.dead.prod.exchange"));
                    rabbitTemplate.setRoutingKey(env.getProperty("mq.kill.item.success.kill.dead.prod.routing.key"));
                    //发送消息
                    rabbitTemplate.convertAndSend(info, new MessagePostProcessor() {
                        @Override
                        public Message postProcessMessage(Message message) throws AmqpException {
                            MessageProperties mp=message.getMessageProperties();
                            mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);   //设置消息的传输模式  持久化  可靠
                            mp.setHeader(AbstractJavaTypeMapper.DEFAULT_CONTENT_CLASSID_FIELD_NAME,KillSuccessUserInfo.class);

                            //TODO：动态设置TTL(为了测试方便，暂且设置10s)   //这里时间是用String   long转化位string  一般时间用的是long表示
                            mp.setExpiration(env.getProperty("mq.kill.item.success.kill.expire"));
                            return message;
                        }
                    });
                }
            }
        }catch (Exception e){
            log.error("秒杀成功后生成抢购订单-发送信息入死信队列，等待着一定时间失效超时未支付的订单-发生异常，消息为：{}",orderCode,e.fillInStackTrace());
        }
    }

    /**
     * 秒杀时异步发送Mq消息
     * @param killDto
     */
    public void sendKillExecuteMqMsg(final KillDto killDto){
        try {
            if (killDto!=null){
                rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
                rabbitTemplate.setExchange(env.getProperty("mq.kill.item.execute.limit.queue.exchange"));
                rabbitTemplate.setRoutingKey(env.getProperty("mq.kill.item.execute.limit.queue.routing.key"));
                rabbitTemplate.convertAndSend(killDto, new MessagePostProcessor() {
                    @Override
                    public Message postProcessMessage(Message message) throws AmqpException {
                        MessageProperties mp=message.getMessageProperties();
                        mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        mp.setHeader(AbstractJavaTypeMapper.DEFAULT_CONTENT_CLASSID_FIELD_NAME,KillDto.class);
                        return message;
                    }
                });
            }
        }catch (Exception e){
            log.error("秒杀时异步发送Mq消息-发生异常，消息为：{}",killDto,e.fillInStackTrace());
        }
    }
}




























