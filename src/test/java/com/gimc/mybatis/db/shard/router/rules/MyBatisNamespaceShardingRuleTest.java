package com.gimc.mybatis.db.shard.router.rules;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.gimc.mybatis.db.shard.entities.Tweet;
import com.gimc.mybatis.db.shard.router.rules.mybatis.MyBatisNamespaceShardingRule;
import com.gimc.mybatis.db.shard.router.support.MyBatisRoutingFact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.gimc.mybatis.db.shard.router.rules.support.ModFunction;
import com.gimc.mybatis.db.shard.support.utils.CollectionUtils;

@Test
public class MyBatisNamespaceShardingRuleTest {

    public static final String DEFAULT_TYPE_PATTEN = "com.alibaba.cobar.client.entity.Tweet";
    public static final String DEFAULT_SHARDING_PATTERN = "id>=10000 and id < 20000";
    public static final String[] DEFAULT_SHARDS = {"shard1", "shard2"};

    private MyBatisNamespaceShardingRule rule;

    @BeforeMethod
    protected void setUp() throws Exception {
        rule = new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", DEFAULT_SHARDING_PATTERN);
    }

    @AfterMethod
    protected void tearDown() throws Exception {
        rule = null;
    }

    public void testShardIdAssemblyNormally() {
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(2, shards.size());

        for (String shard : shards) {
            assertTrue(ArrayUtils.contains(DEFAULT_SHARDS, shard));
        }
    }

    public void testShardIdAssemblyAbnormally() {
        try {
            new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, null, DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testShardIdAssemblyWithCustomActionPatternSeparatorNormally() {
        rule.setActionPatternSeparator(";");
        List<String> shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(1, shards.size());
        assertEquals("shard1,shard2", shards.get(0));

        rule = new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard1;shard2", DEFAULT_SHARDING_PATTERN);
        rule.setActionPatternSeparator(";");
        shards = null;
        shards = rule.action();
        assertTrue(CollectionUtils.isNotEmpty(shards));
        assertEquals(2, shards.size());
        for (String shard : shards) {
            assertTrue(ArrayUtils.contains(DEFAULT_SHARDS, shard));
        }
    }

    public void testShardIdAssemblyWithCustomActionPatternSeparatorAbnormally() {
        try {
            rule.setActionPatternSeparator(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testRuleTypePatternMatchingNormally() {
        Tweet t = new Tweet();
        t.setId(15000L);
        t.setTweet("anything");
        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", t);
        assertTrue(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", t);
        assertTrue(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Follower.update", t);
        assertFalse(rule.isDefinedAt(fact));
    }

    public void testRuleTypePatternMatchingAbnormally() {
        // abnormal parameter 
        try {
            rule.isDefinedAt(null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        // construction abnormally
        try {
            rule = new MyBatisNamespaceShardingRule(null, "shard1,shard2", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
        try {
            rule = new MyBatisNamespaceShardingRule("", "shard1,shard2", DEFAULT_SHARDING_PATTERN);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testRuleShardingPatternMatchingNormally() {
        Tweet t = new Tweet();
        t.setId(15000L);
        t.setTweet("anything");
        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", t);
        assertTrue(rule.isDefinedAt(fact));

        t.setId(20000001L);
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", t);
        assertFalse(rule.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", null);
        assertFalse(rule.isDefinedAt(fact));

        Map<String, Long> ctx = new HashMap<String, Long>();
        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.update", ctx);
        assertFalse(rule.isDefinedAt(fact));

        ctx.put("id", 18000L);
        assertTrue(rule.isDefinedAt(fact));

    }

    public void testRuleShardingPatternMatchingAbnormally() {
        try {
            new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", null);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }

        try {
            new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard1,shard2", "");
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testRuleShardingPatternWithCustomFunctions() throws Exception {
        String shardingExpression = "mod.apply(id)==3";
        MyBatisNamespaceShardingRule r = new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, StringUtils
            .join(DEFAULT_SHARDS, ","), shardingExpression);
        Map<String, Object> functions = new HashMap<String, Object>();
        functions.put("mod", new ModFunction(18L));
        r.setFunctionMap(functions);

        Tweet t = new Tweet();
        t.setId(3L);
        t.setTweet("anything");
        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", t);
        assertTrue(r.isDefinedAt(fact));
    }

    public void testRuleExpressionEvaluationWithSimpleTypeRoutingFact() {
        MyBatisNamespaceShardingRule r = new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard2", "$ROOT.startsWith(\"A\")");

        MyBatisRoutingFact fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", "Arron");
        assertTrue(r.isDefinedAt(fact));

        r = new MyBatisNamespaceShardingRule(DEFAULT_TYPE_PATTEN, "shard2", "startsWith(\"A\")");
        assertTrue(r.isDefinedAt(fact));

        fact = new MyBatisRoutingFact("com.alibaba.cobar.client.entity.Tweet.create", "Donald");
        assertFalse(r.isDefinedAt(fact));
    }
}
