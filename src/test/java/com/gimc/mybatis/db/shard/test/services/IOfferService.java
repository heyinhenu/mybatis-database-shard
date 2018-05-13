package com.gimc.mybatis.db.shard.test.services;

import com.gimc.mybatis.db.shard.entities.Offer;
import java.util.List;

public interface IOfferService {

    void createOffersInBatch(List<Offer> offers);

}
