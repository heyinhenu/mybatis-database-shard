package com.gimc.mybatis.db.shard.test.services;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.gimc.mybatis.db.shard.entities.Offer;
import com.gimc.mybatis.db.shard.support.vo.BatchInsertTask;

public class AbnormalOfferService extends NormalOfferService {

    @Transactional
    public void createOffersInBatch(List<Offer> offers) {
        getSqlMapClientTemplate().insert("com.alibaba.cobar.client.entities.Offer.batchInsert", new BatchInsertTask(offers));
        throw new RuntimeException("exception to trigger rollback");
    }

}
