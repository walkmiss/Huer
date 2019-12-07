package com.kuang.springcloud.util;

import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: jiabin
 * @create: 2019/12/320:57
 * @description: 使用redis实现分布式锁
 **/
public class RedisLock implements Lock {

    StringRedisTemplate stringRedisTemplate;
    String resourceName;  //加锁的资源名称
    int timeout; //超时时间

    Lock lock = new ReentrantLock();

    //构建一把锁


    public RedisLock(StringRedisTemplate stringRedisTemplate, String resourceName, int timeout) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.resourceName = "lock_" + resourceName;
        this.timeout = timeout;
    }

    @Override
    public void lock() {
        lock.lock();
        try{

            //抢redis锁的代码  只会有一个线程 如果没抢到就继续抢
            while (!tryLock()) {
                //订阅指定的redis主题，等待锁释放后重新尝试去获取
                stringRedisTemplate.execute(new RedisCallback<Long>() {
                    @Override
                    public Long doInRedis(RedisConnection redisConnection) throws DataAccessException {
                        try {
                            CountDownLatch waiter = new CountDownLatch(1);
                            redisConnection.subscribe((message, pattern) -> {
                                //收到消息，不管结果如何，再次抢锁
                                waiter.countDown();
                            }, ("release_lock" + resourceName).getBytes());
                            //等有通知，才继续循环
                            waiter.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return 0L;
                    }
                });
            }
        }finally {
            lock.unlock();
        }
    }

    @Override
    public boolean tryLock() { //尝试获取锁
        Boolean setResult = stringRedisTemplate.execute(new RedisCallback<Boolean>() {
            @Override
            public Boolean doInRedis(RedisConnection redisConnection) throws DataAccessException {
                Boolean result = redisConnection.set(resourceName.getBytes(), "YY".getBytes(),
                        Expiration.seconds(timeout), RedisStringCommands.SetOption.ifAbsent());
                return result;
            }
        });
        return setResult;
    }

    @Override
    public void unlock() {
        //释放锁资源
        stringRedisTemplate.delete(resourceName);
        //通过redis发布订阅机制发送一个通知给其他等待的请求
        stringRedisTemplate.execute(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection redisConnection) throws DataAccessException {
                Long received = redisConnection.publish(("release_lock" + resourceName).getBytes(),
                        "YY".getBytes());
                return received;
            }
        });
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
