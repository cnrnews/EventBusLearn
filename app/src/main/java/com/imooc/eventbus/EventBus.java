package com.imooc.eventbus;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private static volatile EventBus defaultInstance;
    /**
     * key:     订阅者方法参数的class
     * value:   SubScription 集合列表
     * SubScription: 一个是 subscriber 订阅者 一个是 SubscriberMethod 注解方法的集合
     */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subScriptionsByEventType;
    /**
     * key:     所有订阅者
     * value:   订阅者方法参数的class
     */
    private final Map<Object, List<Class<?>>> typesBySubscriber;

    private EventBus(){
        typesBySubscriber=new HashMap<>();
        subScriptionsByEventType = new HashMap<>();
    }
    public static EventBus getDefault(){
        if (defaultInstance == null){
            synchronized (EventBus.class){
                if (defaultInstance == null){
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    public void register(Object object){
        // 1.解析所有方法封装成 SubScriberMethod 集合
        List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        Class<?> objectClass = object.getClass();
        Method[] declaredMethods = objectClass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation!=null){
                // 解析所有 Subscribe 属性
                Class<?>[] parameterTypes = method.getParameterTypes();
                SubscriberMethod subScriberMethod = new SubscriberMethod(
                        method,parameterTypes[0],annotation.threadMode(),annotation.priority(),
                        annotation.sticky()
                );
                subscriberMethods.add(subScriberMethod);
            }
        }
        //2.存放到 subScriptionsByEventType 里面去
        for (SubscriberMethod method : subscriberMethods) {
            subscriber(object,method);
        }
    }

    private void subscriber(Object object, SubscriberMethod subscriberMethod) {

        Class<?> eventType = subscriberMethod.eventType;

        CopyOnWriteArrayList<Subscription> subScriptions = subScriptionsByEventType.get(eventType);
        if (subScriptions==null){
            subScriptions = new CopyOnWriteArrayList<>();
            subScriptionsByEventType.put(eventType,subScriptions);
        }

        Subscription subScription= new Subscription(object,subscriberMethod);
        subScriptions.add(subScription);

        List<Class<?>> eventTypes = typesBySubscriber.get(object);
        if (eventType == null) {
            eventTypes = new ArrayList<>();
            typesBySubscriber.put(object,eventTypes);
        }

        if (!eventTypes.contains(eventType)){
            eventTypes.add(eventType);
        }


    }
    /**
     * 取消订阅
     * @param obj
     */
    public synchronized void unregister(Object obj) {
        //获取该对象订阅方法的所有参数类型
        List<Class<?>> eventTypes = typesBySubscriber.get(obj);
        if (eventTypes != null) {
            for (Class<?> eventType : eventTypes) {
                //获取Subscription集合
                removeObject(obj, eventType);
            }

            // 移除
            typesBySubscriber.remove(obj);
        }
    }

    private void removeObject(Object obj, Class<?> eventType) {
        List<Subscription> subscriptions = subScriptionsByEventType.get(eventType);
        if (subscriptions!=null){
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                if (subscriptions.get(i).subscriber == obj) {//判断是不是该对象的方法
                    // 将订阅信息从集合中移除
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }

    }
    
    public void post(Object event){
        // 遍历subScriptionsByEventType，找到符合的方法调用方法的 method.invoke() 执行，要注意线程切换
        Class<?> eventType = event.getClass();
        // 找到符合的方法调用方法的 method.invoke() 执行
        CopyOnWriteArrayList<Subscription> subscriptions = subScriptionsByEventType.get(eventType);
        if (subscriptions!=null){
            for (Subscription subscription : subscriptions) {
                executeMethod(subscription,event);
            }
        }
    }

    private void executeMethod(Subscription subscription, Object event) {
        ThreadMode threadMode = subscription.subscriberMethod.threadMode;
        boolean isMainThread = Looper.getMainLooper() == Looper.myLooper();
        switch (threadMode){
            case POSTING:
                invokeMethod(subscription,event);
                break;
            case MAIN:
                if (isMainThread){
                    invokeMethod(subscription,event);
                }else{
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invokeMethod(subscription,event);
                        }
                    });
                }
                break;
            case ASYNC:
                AsyncPoster.enqueue(subscription,event);
                break;
            case BACKGROUND:
                if (!isMainThread){
                    invokeMethod(subscription,event);
                }else{
                    AsyncPoster.enqueue(subscription,event);
                }
                break;
        }
    }

    private void invokeMethod(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber,event);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
