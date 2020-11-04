package com.debug.kill.server.service;

import com.debug.kill.model.entity.ItemKill;

import java.util.List;

/**
 * Created by Administrator
 */
public interface IItemService {

    List<ItemKill> getKillItems() throws Exception;

    ItemKill getKillDetail(Integer id) throws Exception;
}
