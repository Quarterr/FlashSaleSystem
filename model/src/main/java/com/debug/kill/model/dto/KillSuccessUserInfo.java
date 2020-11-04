package com.debug.kill.model.dto;/**
 * Created by Administrator on 2019/6/21.
 */

import com.debug.kill.model.entity.ItemKillSuccess;
import lombok.Data;

import java.io.Serializable;

/**
 * dto  感觉是自己生成的数据库中的一张表
 * @Date: 2019/6/21 22:02
 **/
@Data
public class KillSuccessUserInfo extends ItemKillSuccess implements Serializable{

    private String userName;

    private String phone;

    private String email;

    private String itemName;

    @Override
    public String toString() {
        return super.toString()+"\nKillSuccessUserInfo{" +
                "userName='" + userName + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", itemName='" + itemName + '\'' +
                '}';
    }
}