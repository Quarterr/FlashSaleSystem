package com.debug.kill.server.service;/**
 * Created by Administrator on 2019/6/21.
 */

import com.debug.kill.model.dto.KillSuccessUserInfo;
import com.debug.kill.model.entity.ItemKillSuccess;
import com.debug.kill.model.mapper.ItemKillSuccessMapper;
import com.debug.kill.server.dto.KillDto;
import com.debug.kill.server.dto.MailDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ通用的消息接收服务
 * @Author:debug (SteadyJack)
 * @Date: 2019/6/21 21:47
 **/
@Service
public class RabbitReceiverService {

    public static final Logger log= LoggerFactory.getLogger(RabbitReceiverService.class);

    @Autowired
    private MailService mailService;

    @Autowired
    private Environment env;    //整体代码环境

    @Autowired
    private ItemKillSuccessMapper itemKillSuccessMapper;

    @Autowired
    private IKillService killService;



    /**
     * 秒杀异步邮件通知-接收消息      消费者进行接收
     */
    @RabbitListener(queues = {"${mq.kill.item.success.email.queue}"},containerFactory = "singleListenerContainer")   //listener 要监听那个队列  采用单一消费者实例
    public void consumeEmailMsg(KillSuccessUserInfo info){
        try {
            log.info("秒杀异步邮件通知-接收消息:{}",info);

            //TODO:真正的发送邮件....
            //简单文本
            //MailDto dto=new MailDto(env.getProperty("mail.kill.item.success.subject"),"这是测试内容",new String[]{info.getEmail()});
            //mailService.sendSimpleEmail(dto);

            //花哨文本
            final String content=String.format(env.getProperty("mail.kill.item.success.content"),info.getItemName(),info.getCode());
            //得到dto  主题的话直接在application properties中    new String 是一个接收者？？
            MailDto dto=new MailDto(env.getProperty("mail.kill.item.success.subject"),content,new String[]{info.getEmail()});
            mailService.sendHTMLMail(dto);

        }catch (Exception e){
            log.error("秒杀异步邮件通知-接收消息-发生异常：",e.fillInStackTrace());
        }
    }

    /**
     * 用户秒杀成功后超时未支付-监听者    监听真正队列
     * @param info
     */
    @RabbitListener(queues = {"${mq.kill.item.success.kill.dead.real.queue}"},containerFactory = "singleListenerContainer")
    public void consumeExpireOrder(KillSuccessUserInfo info){
        try {
            log.info("用户秒杀成功后超时未支付-监听者-接收消息:{}",info);

            if (info!=null){
                //查询此时的状态
                ItemKillSuccess entity=itemKillSuccessMapper.selectByPrimaryKey(info.getCode());
                if (entity!=null && entity.getStatus().intValue()==0){   //-1  无效（超时未支付） 0（秒杀成功未付款） 1（已付款）  2（已取消）  我们只关心状态0
                    itemKillSuccessMapper.expireOrder(info.getCode());   //失效这个订单记录 就是把status改成-1   这里会再判断一下，只有用户是0（未付款的状态下）我们才会进行失效操作。（就只是再判断一次其实没啥用说起来是为了保证安全）
                }
            }
        }catch (Exception e){
            log.error("用户秒杀成功后超时未支付-监听者-发生异常：",e.fillInStackTrace());
        }
    }



    /**
     * 秒杀时异步接收Mq消息-监听者
     * @param dto
     */
    @RabbitListener(queues = {"${mq.kill.item.execute.limit.queue.name}"},containerFactory = "multiListenerContainer")
    public void consumeKillExecuteMqMsg(KillDto dto){
        try {
            if (dto!=null){
                //采用任何一种加分布锁的处理方法都是可行的
                killService.killItemV4(dto.getKillId(),dto.getUserId());
            }
        }catch (Exception e){
            log.error("用户秒杀成功后超时未支付-监听者-发生异常：",e.fillInStackTrace());
        }
    }
}












