package com.imooc.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncPoster implements Runnable{

    Subscription subscription;
    Object event;

    static ExecutorService executorService = Executors.newCachedThreadPool();

    public AsyncPoster(Subscription subscription, Object event) {
        this.subscription = subscription;
        this.event = event;
    }
    public static void enqueue(Subscription subscription, Object event) {
        AsyncPoster asyncPoster = new AsyncPoster(subscription, event);
        executorService.execute(asyncPoster);
    }

    @Override
    public void run() {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber,event);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
