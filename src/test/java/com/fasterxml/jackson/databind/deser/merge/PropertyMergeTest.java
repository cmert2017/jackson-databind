package com.fasterxml.jackson.databind.deser.merge;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;

/**
 * Tests to make sure that the new "merging" property of
 * <code>JsonSetter</code> annotation works as expected.
 * 
 * @since 2.9
 */
@SuppressWarnings("serial")
public class PropertyMergeTest extends BaseMapTest
{
    static class Config {
        @JsonSetter(merge=OptBoolean.TRUE)
        public AB loc = new AB(1, 2);

        protected Config() { }
        public Config(int a, int b) {
            loc = new AB(a, b);
        }
    }

    static class NonMergeConfig {
        public AB loc = new AB(1, 2);
    }

    // another variant where all we got is a getter
    static class NoSetterConfig {
        AB _value = new AB(1, 2);
 
        @JsonSetter(merge=OptBoolean.TRUE)
        public AB getValue() { return _value; }
    }

    static class AB {
        public int a;
        public int b;

        protected AB() { }
        public AB(int a0, int b0) {
            a = a0;
            b = b0;
        }
    }

    // Custom type that would be deserializable by default
    static class StringReference extends AtomicReference<String> {
        public StringReference(String str) {
            set(str);
        }
    }

    static class MergedMap
    {
        @JsonSetter(merge=OptBoolean.TRUE)
        public Map<String,String> values = new LinkedHashMap<>();
        {
            values.put("a", "x");
        }
    }

    static class MergedReference
    {
        @JsonSetter(merge=OptBoolean.TRUE)
        public StringReference value = new StringReference("default");
    }

    static class MergedX<T>
    {
        @JsonSetter(merge=OptBoolean.TRUE)
        public T value;

        public MergedX(T v) { value = v; }
        protected MergedX() { }
    }
    
    // // // Classes with invalid merge definition(s)

    static class CantMergeInts {
        @JsonSetter(merge=OptBoolean.TRUE)
        public int value;
    }

    /*
    /********************************************************
    /* Test methods, POJO merging
    /********************************************************
     */

    private final ObjectMapper MAPPER = new ObjectMapper()
            // 26-Oct-2016, tatu: Make sure we'll report merge problems by default
            .disable(MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE)
    ;

    public void testBeanMergingViaProp() throws Exception
    {
        Config config = MAPPER.readValue(aposToQuotes("{'loc':{'b':3}}"), Config.class);
        assertEquals(1, config.loc.a);
        assertEquals(3, config.loc.b);

        config = MAPPER.readerForUpdating(new Config(5, 7))
                .readValue(aposToQuotes("{'loc':{'b':2}}"));
        assertEquals(5, config.loc.a);
        assertEquals(2, config.loc.b);
    }

    public void testBeanMergingViaType() throws Exception
    {
        // by default, no merging
        NonMergeConfig config = MAPPER.readValue(aposToQuotes("{'loc':{'a':3}}"), NonMergeConfig.class);
        assertEquals(3, config.loc.a);
        assertEquals(0, config.loc.b); // not passed, nor merge from original

        // but with type-overrides
        ObjectMapper mapper = new ObjectMapper();
        mapper.configOverride(AB.class).setSetterInfo(
                JsonSetter.Value.forMerging());
        config = mapper.readValue(aposToQuotes("{'loc':{'a':3}}"), NonMergeConfig.class);
        assertEquals(3, config.loc.a);
        assertEquals(2, config.loc.b); // original, merged
    }

    public void testBeanMergingViaGlobal() throws Exception
    {
        // but with type-overrides
        ObjectMapper mapper = new ObjectMapper()
                .setDefaultSetterInfo(JsonSetter.Value.forMerging());
        NonMergeConfig config = mapper.readValue(aposToQuotes("{'loc':{'a':3}}"), NonMergeConfig.class);
        assertEquals(3, config.loc.a);
        assertEquals(2, config.loc.b); // original, merged

        // also, test with bigger POJO type; just as smoke test
        FiveMinuteUser user0 = new FiveMinuteUser("Bob", "Bush", true, FiveMinuteUser.Gender.MALE,
                new byte[] { 1, 2, 3, 4, 5 });
        FiveMinuteUser user = mapper.readerFor(FiveMinuteUser.class)
                .withValueToUpdate(user0)
                .readValue(aposToQuotes("{'name':{'last':'Brown'}}"));
        assertEquals("Bob", user.getName().getFirst());
        assertEquals("Brown", user.getName().getLast());
    }

    // should even work with no setter
    public void testBeanMergingWithoutSetter() throws Exception
    {
        NoSetterConfig config = MAPPER.readValue(aposToQuotes("{'value':{'b':99}}"),
                NoSetterConfig.class);
        assertEquals(99, config._value.b);
        assertEquals(1, config._value.a);
    }

    /*
    /********************************************************
    /* Test methods, Map merging
    /********************************************************
     */

    public void testMapMerging() throws Exception
    {
        MergedMap v = MAPPER.readValue(aposToQuotes("{'values':{'c':'y'}}"), MergedMap.class);
        assertEquals(2, v.values.size());
        assertEquals("y", v.values.get("c"));
        assertEquals("x", v.values.get("a"));
    }

    
    /*
    /********************************************************
    /* Test methods, reference types
    /********************************************************
     */

    public void testReferenceMerging() throws Exception
    {
        MergedReference result = MAPPER.readValue(aposToQuotes("{'value':'override'}"),
                MergedReference.class);
        assertEquals("override", result.value.get());
    }

    /*
    /********************************************************
    /* Test methods, failure checking
    /********************************************************
     */

    public void testInvalidPropertyMerge() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper()
                .disable(MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE);
        
        try {
            mapper.readValue("{\"value\":3}", CantMergeInts.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "can not be merged");
        }
    }
}
