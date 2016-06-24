package com.dotmarketing.business.cache.provider.guava;

import com.dotcms.enterprise.cache.provider.CacheProviderAPI;
import com.dotcms.repackage.com.google.common.cache.Cache;
import com.dotcms.repackage.com.google.common.cache.CacheBuilder;
import com.dotcms.repackage.com.google.common.cache.CacheLoader;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.cache.provider.CacheProvider;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jonathan Gamba
 *         Date: 9/1/15
 */
public class GuavaCache extends CacheProvider {

    private static final long serialVersionUID = 1348649382678659786L;

    private Boolean isInitialized = false;

    static final String DEFAULT_CACHE = CacheProviderAPI.DEFAULT_CACHE;
    static final String LIVE_CACHE_PREFIX = CacheProviderAPI.LIVE_CACHE_PREFIX;
    static final String WORKING_CACHE_PREFIX = CacheProviderAPI.WORKING_CACHE_PREFIX;

    private final ConcurrentHashMap<String, Cache<String, Object>> groups = new ConcurrentHashMap<>();
    private final HashSet<String> availableCaches = new HashSet<>();

    private NullCallable nullCallable = new NullCallable();

    @Override
    public String getName () {
        return "Guava Memory Cache";
    }

    @Override
    public String getKey () {
        return "LocalGuavaMem";
    }

    @Override
    public void init () {

        availableCaches.add(DEFAULT_CACHE);

        Iterator<String> it = Config.getKeys();
        while ( it.hasNext() ) {

            String key = it.next();
            if ( key == null ) {
                continue;
            }

            if ( key.startsWith("cache.") ) {

                String cacheName = key.split("\\.")[1];
                if ( key.endsWith(".size") ) {
                    int inMemory = Config.getIntProperty(key, 0);
                    availableCaches.add(cacheName.toLowerCase());
                    Logger.info(this.getClass(), "***\t Cache Config Memory : " + cacheName + ": " + inMemory);
                }

            }
        }

        isInitialized = true;
    }

    @Override
    public boolean isInitialized () throws Exception {
        return isInitialized;
    }

    @Override
    public void put ( String group, String key, Object content ) {

        //Get the cache for the given group
        Cache cache = getCache(group);

        //Add the given content to the group and for a given key
        cache.put(key, content);
    }

    @Override
    public Object get ( String group, String key ) {

        //Get the cache for the given group
        Cache cache = getCache(group);

        Object foundObject = null;
        try {

            //Get the content from the group and for a given key
            foundObject = cache.get(key, nullCallable);
        } catch ( CacheLoader.InvalidCacheLoadException e ) {
            //Do nothing, we are expecting this error when no value for a key is found
        } catch ( Exception e ) {
            Logger.error(this.getClass(), "Error getting value from cache from group [" + group + "] and key [" + key + "].", e);
        }

        return foundObject;
    }

    @Override
    public void remove ( String group ) {

        //Get the cache for the given group
        Cache<String, Object> cache = getCache(group);

        //Invalidates the Cache for the given group
        cache.invalidateAll();

        //Remove this group from the global list of cache groups
        groups.remove(group);
    }

    @Override
    public void remove ( String group, String key ) {

        //Get the cache for the given group
        Cache<String, Object> cache = getCache(group);

        //Invalidates from Cache a key from a given group
        cache.invalidate(key);
    }

    @Override
    public void removeAll () {

        Set<String> currentGroups = new HashSet<>();
        currentGroups.addAll(getGroups());

        for ( String group : currentGroups ) {
            remove(group);
        }

        groups.clear();
    }

    @Override
    public Set<String> getGroups () {
        return groups.keySet();
    }

    @Override
    public Set<String> getKeys ( String group ) {

        Set<String> keys = new HashSet<>();

        Cache<String, Object> cache = getCache(group);
        Map<String, Object> m = cache.asMap();

        if ( m != null ) {
            keys = m.keySet();
        }

        return keys;
    }

    @Override
    public List<Map<String, Object>> getStats () {

        List<Map<String, Object>> list = new ArrayList<>();

        Set<String> currentGroups = new HashSet<>();
        currentGroups.addAll(getGroups());

        Cache defaultCache = getCache(DEFAULT_CACHE);
        for ( String group : currentGroups ) {

            Map<String, Object> stats = new HashMap<>();
            stats.put("name", getName());
            stats.put("key", getKey());
            stats.put("cache", getCache(group));
            stats.put("region", group);
            stats.put("toDisk", false);

            Cache foundCache = getCache(group);
            stats.put("memory", foundCache.size());
            stats.put("CacheStats", foundCache.stats());
            stats.put("disk", -1);

            boolean isDefault = (!DEFAULT_CACHE.equals(group) && foundCache.equals(defaultCache));
            stats.put("isDefault", isDefault);

            int configured = isDefault
                    ? Config.getIntProperty("cache." + DEFAULT_CACHE + ".size")
                    : (Config.getIntProperty("cache." + group + ".size", -1) != -1)
                    ? Config.getIntProperty("cache." + group + ".size")
                    : (group.startsWith(WORKING_CACHE_PREFIX) && Config.getIntProperty("cache." + WORKING_CACHE_PREFIX + ".size", -1) != -1)
                    ? Config.getIntProperty("cache." + WORKING_CACHE_PREFIX + ".size")
                    : (group.startsWith(LIVE_CACHE_PREFIX) && Config.getIntProperty("cache." + LIVE_CACHE_PREFIX + ".size", -1) != -1)
                    ? Config.getIntProperty("cache." + LIVE_CACHE_PREFIX + ".size")
                    : Config.getIntProperty("cache." + DEFAULT_CACHE + ".size");
            stats.put("configuredSize", configured);

            list.add(stats);
        }

        return list;
    }

    @Override
    public void shutdown () {
        Logger.info(this.getClass(), "===== Calling shutdown [" + getName() + "].");
        isInitialized = false;
    }

    private Cache<String, Object> getCache ( String cacheName ) {

        if ( cacheName == null ) {
            throw new DotStateException("Null cache region passed in");
        }

        cacheName = cacheName.toLowerCase();
        Cache<String, Object> cache = groups.get(cacheName);

        // init cache if it does not exist
        if ( cache == null ) {
            synchronized (cacheName.intern()) {
                cache = groups.get(cacheName);
                if ( cache == null ) {

                    boolean separateCache = (
                    		Config.getBooleanProperty("cache.separate.caches.for.non.defined.regions", true) 
                    		|| availableCaches.contains(cacheName) 
                    		|| DEFAULT_CACHE.equals(cacheName) 
                    		|| cacheName.startsWith(LIVE_CACHE_PREFIX) 
                    		|| cacheName.startsWith(WORKING_CACHE_PREFIX)
                    	);

                    if ( separateCache ) {
                        int size;
                        if ( cacheName.startsWith(LIVE_CACHE_PREFIX) ) {
                            size = Config.getIntProperty("cache." + cacheName + ".size", -1);
                            if ( size < 0 ) {
                                size = Config.getIntProperty("cache." + LIVE_CACHE_PREFIX + ".size", -1);
                            }
                        } else if ( cacheName.startsWith(WORKING_CACHE_PREFIX) ) {
                            size = Config.getIntProperty("cache." + cacheName + ".size", -1);
                            if ( size < 0 ) {
                                size = Config.getIntProperty("cache." + WORKING_CACHE_PREFIX + ".size", -1);
                            }
                        } else {
                            size = Config.getIntProperty("cache." + cacheName + ".size", -1);
                        }

                        if ( size == -1 ) {
                            size = Config.getIntProperty("cache." + DEFAULT_CACHE + ".size", 100);
                        }

                        Logger.info(this.getClass(), "***\t Building Cache : " + cacheName + ", size:" + size + ",Concurrency:" + Config.getIntProperty("cache.concurrencylevel", 32));
                        CacheBuilder<Object, Object> cb = CacheBuilder
                                .newBuilder()
                                .maximumSize(size)
                                .concurrencyLevel(Config.getIntProperty("cache.concurrencylevel", 32));


                        cache = cb.build();
                        groups.put(cacheName, cache);

                    } else {
                        Logger.info(this.getClass(), "***\t No Cache for   : " + cacheName + ", using " + DEFAULT_CACHE);
                        cache = getCache(DEFAULT_CACHE);
                        groups.put(cacheName, cache);
                    }
                }
            }
        }

        return cache;
    }

    private class NullCallable implements Callable {
        public Object call () throws Exception {
            return null;
        }
    }

}