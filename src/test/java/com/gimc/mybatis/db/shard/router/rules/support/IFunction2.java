package com.gimc.mybatis.db.shard.router.rules.support;

public interface IFunction2<I, O> {

    O apply(I input);
}
