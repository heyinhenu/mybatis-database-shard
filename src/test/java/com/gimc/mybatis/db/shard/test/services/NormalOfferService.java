package com.gimc.mybatis.db.shard.test.services;

import com.gimc.mybatis.db.shard.entities.Offer;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.gimc.mybatis.db.shard.support.vo.BatchInsertTask;

public class NormalOfferService extends AbstractOfferService {

    @Transactional
    public void createOffersInBatch(List<Offer> offers) {
        getSqlMapClientTemplate().insert("com.alibaba.cobar.client.entities.Offer.batchInsert", new BatchInsertTask(offers));
    }

}
