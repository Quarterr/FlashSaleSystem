package com.debug.kill.server;/**
 * Created by Administrator on 2019/6/13.
 */

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Author:debug (SteadyJack)
 * @Date: 2019/6/13 22:50
 **/

//加这个注解表示整个项目的入口是这个类   MainApplication
@SpringBootApplication
@ImportResource(value = {"classpath:spring/spring-jdbc.xml"})
//@ImportResource(value = "classpath:spring/spring-shiro.xml")
@MapperScan(basePackages = "com.debug.kill.model.mapper")
@EnableScheduling
public class MainApplication extends SpringBootServletInitializer{

    @Override
    //把整个系统的加了注解的资源加到IoC容器中，进行启动
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(MainApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class,args);
    }

}