package com.gimc.mybatis.db.shard.router.rules;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.gimc.mybatis.db.shard.entities.Tweet;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisSqlActionShardingRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.gimc.mybatis.db.shard.router.rules.support.ModFunction;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;

/**
 * TODO Comment of MyBatisSqlActionShardingRuleTest
 *
 * @author fujohnwang
 * @see {@link MyBatisNamespaceShardingRuleTest} for more test scenarios.
 */
@Test
public class MyBatisSqlActionShardingRuleTest {

    // almost copied from MyBatisNamespaceShardingRuleTest, although a same top class is better.
    public static final String DEFAULT_TYPE_PATTEN = "com.alibaba.cobar.client.entity.Tweet.create";
    public static final String DEFAULT_SHARDING_PATTERN = "id>=10000 and id < 20000";
    public static final String[] DEFAULT_SHARDS = {"shard1", "shard2"};

    private MyBatisSqlActionShardingRule rule;

    @BeforeMethod
    protected void setUp() throws Exception {
        rule = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", DEFAULT_SHARDING_PATTERN);
    }

    @AfterMethod
    protected void tearDown() throws Exception {
        rule = null;
    }

    public void testSqlActionShardingRuleConstructionAbnormally() {
        try {
            rule = new MyBatisSqlActionShardingRule(null, "shard1,shard2", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule = new MyBatisSqlActionShardingRule("", "shard1,shard2", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, null, DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", "");
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testSqlActionShardingRulePatternMatchingNormally() {
        Tweet t = new Tweet();
        t.setId(15000L);
        t.setTweet("anything");

        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", t);
        assertTrue(rule.isDefinedAt(fact));
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(2, shards.size());
        for (String shard : shards) {
            assertTrue(ArrayUtils.contains(DEFAULT_SHARDS, shard));
        }

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", t);
        assertFalse(rule.isDefinedAt(fact));

        t.setId(20000L);
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", t);
        assertFalse(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", null);
        assertFalse(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", new Object());
        assertFalse(rule.isDefinedAt(fact));
    }

    public void testSqlActionShardingRulePatternMatchingAbnormally() {
        try {
            rule.setActionPatternSeparator(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            rule.isDefinedAt(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testSqlActionShardingRuleWithCustomFunctions() {
        MyBatisSqlActionShardingRule r = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", "mod.apply(id)==3");
        Map<String, Object> functions = new HashMap<String, Object>();
        functions.put("mod", new ModFunction(18L));
        r.setFunctionMap(functions);

        Tweet t = new Tweet();
        t.setId(21L);
        t.setTweet("anything");
        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", t);
        assertTrue(r.isDefinedAt(fact));
    }

    public void testSqlActionShardingRuleWithSimpleContextObjectType() {
        MyBatisSqlActionShardingRule r = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1", "$ROOT.startsWith(\"J\")");
        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", "Jack");
        assertTrue(r.isDefinedAt(fact));

        r = new MyBatisSqlActionShardingRule(DEFAULT_TYPE_PATTEN, "shard1", "startsWith(\"J\")");
        assertTrue(r.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", "Amanda");
        assertFalse(r.isDefinedAt(fact));


    }
}
