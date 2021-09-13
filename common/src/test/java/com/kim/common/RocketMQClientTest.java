package com.kim.common;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.CountDownLatch2;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.jupiter.api.*;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author huangjie
 * @description 本次测试demo的rocketmq服务安装在linux平台，版本为4.4.0
 * @date 2021-9-3
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RocketMQClientTest {

    private DefaultMQProducer producer;


    @BeforeEach
    public void producerBuilder() throws MQClientException {

        //实例化消息生产者，可从构造方法传入默认消息组名
        DefaultMQProducer producer = new DefaultMQProducer("DEFAULT_GROUP");
        producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
        producer.setNamesrvAddr("101.34.74.116:9876");
        producer.setSendMsgTimeout(10000);
        this.producer = producer;
        this.producer.start();


    }

    @AfterEach
    public void shutdownProducer() throws MQClientException {

        //关闭生产者
        producer.shutdown();


    }

    /**
     * 同步消息发送
     */
    @Test
    @DisplayName("发送同步消息")
    public void sendMsg() throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {


        //连续发送100条消息
        for (int i = 0; i < 10; i++) {
            //创建单条消息，指定topic，tag(相当于子topic)，和消息体,可以在控制台或者命令行创建topic
            Message message = new Message("TopicTest", "TagE", ("myMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            //发送消息，注意数据文件放在$home/user/store;磁盘空间要大于4G，否则会发送不成功报超时
            SendResult result = producer.send(message);
            System.out.printf("%s%n", result);
            Thread.sleep(1000);
        }

    }

    @Test
    @DisplayName("发送异步消息")
    public void sendAsyncMsg() throws UnsupportedEncodingException, InterruptedException, RemotingException, MQClientException, MQBrokerException {
        //设置异步发送重试次数为0
        producer.setRetryTimesWhenSendAsyncFailed(0);
        //定义发送多少条异步消息，并加上等待消息发送完毕阻塞锁
        int msgCount = 10;
        CountDownLatch2 countDownLatch2 = new CountDownLatch2(msgCount);
        for (int i = 0; i < msgCount; i++) {
            Message msg = new Message("AsyncTopic", "tagA", "key" + i, ("asyncMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            int finalI = i;
            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    //发送成功
                    System.out.println("第" + finalI + "条消息发送成功");
                    System.out.printf("%s%n", sendResult);
                }

                @Override
                public void onException(Throwable e) {
                    //发送失败
                    System.out.println("第" + finalI + "条消息发送失败");
                    e.printStackTrace();
                }
            });
        }
        countDownLatch2.await(5, TimeUnit.SECONDS);
    }


    @Test
    @DisplayName("单向发送消息")
    public void sendOneway() throws UnsupportedEncodingException, RemotingException, MQClientException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            Message msg = new Message("SendOneway", "tagA", "key" + i, ("oneway" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            //单向发送，不管是否发送成功，没有任何返回结果
            producer.sendOneway(msg);
        }
    }

    /**
     * 顺序消息发送和消费，rocketmq默认是轮询对指定topic绑定的所有队列分别发送消息，
     * 消费消息的时候，是并发接收，因此是不会按严格顺序进行发送和消费的。
     * 顺序消息的机制就是让发送方和消费方都选择同一个队列进行发送和消费消息。
     * 全部消息都在同一个队列进行发送和消费就是全局顺序消费
     * 分别选择不同的队列进行发送和消费则是分区顺序消费
     */
    @Test
    @DisplayName("顺序消息发送")
    public void sendOrderly() throws UnsupportedEncodingException, InterruptedException, RemotingException, MQClientException, MQBrokerException {

        for (int i = 0; i < 10; i++) {
            Message msg = new Message("OrderMsg", "tagC", "key" + i, ("hello orderMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            /**
             * 发送顺序消息，最后一个参数是传入给队列选择器方法的参数,即在select(List<MessageQueue> list, Message message, Object arg)
             * 方法里面的作为第三个参数arg传入
             * 在同一个队列的消息生产和消费将会顺序进行
             */
            SendResult sendResult = producer.send(msg, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> list, Message message, Object arg) {
                    int queueId = (int) arg;
                    return list.get(queueId);
                }
            }, 0);
            System.out.println(String.format("SendResult status:%s, queueId:%d, msg:%s", sendResult.getSendStatus(), sendResult.getMessageQueue().getQueueId(), new String(msg.getBody())));
        }

    }

    @Test
    @DisplayName("发送延时消息")
    public void sendMsgDelay() throws UnsupportedEncodingException, InterruptedException, RemotingException, MQClientException, MQBrokerException {
        for (int i = 0; i < 10; i++) {
            Message message = new Message("DelayMsg", "tagB", "key" + i, ("delayMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            /**
             * 发送消息延时等级，参考下面这个配置类messageDelayLevel属性
             * org.apache.rocketmq.store.config.MessageStoreConfig
             * 目前仅支持以下这么多延时等级，从1~18等级依次对应
             * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
             * 这里以等级3为例
             * */
            message.setDelayTimeLevel(3);
            //同步发送消息
            producer.send(message);
            System.out.println(("key" + i + "的发送时间:") + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date()));
        }
    }

    @Test
    @DisplayName("批量发送消息")
    public void sendBatchMsgs() throws UnsupportedEncodingException, InterruptedException, RemotingException, MQClientException, MQBrokerException {

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(new Message("BatchMsg", "tagA", "key" + i, ("batchMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET)));
        }
        producer.send(messages);
    }

    /**
     * 批量消息列表分割
     * 批量发送消息能显著提高传递小消息的性能。限制是这些批量消息应该有相同的topic，相同的waitStoreMsgOK，
     * 而且不能是延时消息。此外，这一批消息的总大小不应超过4MB。
     * 复杂度只有当你发送大批量时才会增长，你可能不确定它是否超过了大小限制（4MB）
     * 因此，需要将批量消息集合里面的单个大于4MB的消息过滤掉，以及将集合里面分割成不大于4MB的消息子集合
     * 再逐一进行发送
     */
    public static class MsgSplitter implements Iterator<List<Message>> {

        private static final Integer maxCapacity = 1024 * 1024 * 4;
        private Integer offset;
        private List<Message> messages;

        public MsgSplitter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public boolean hasNext() {

            return offset < messages.size();
        }

        @Override
        public List<Message> next() {
            int tmpTotalSize=0;
            List<Message> splitList=new ArrayList<>();
            for(;offset<messages.size();offset++){
                int capacitySimple=calcMessageSize(messages.get(offset));
                if(capacitySimple > maxCapacity){
                    continue;
                }else{
                    tmpTotalSize+=capacitySimple;
                }
                if(tmpTotalSize > maxCapacity){
                    break;
                }else{
                    splitList.add(messages.get(offset));
                }
            }
            return splitList;
        }

        /**
         * 计算单条消息的容量大小
         */
        private int calcMessageSize(Message message) {
            int tmpSize = message.getTopic().length() + message.getBody().length;
            Map<String, String> properties = message.getProperties();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                tmpSize += entry.getKey().length() + entry.getValue().length();
            }
            tmpSize = tmpSize + 20; // 增加⽇日志的开销20字节
            return tmpSize;
        }

    }

    @Test
    @DisplayName("消息分割后批量发送消息")
    public void sendBatchSplitMsgs() throws UnsupportedEncodingException, InterruptedException, RemotingException, MQClientException, MQBrokerException {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(new Message("BatchMsg", "tagA", "key" + i, ("batchMsg" + i).getBytes(RemotingHelper.DEFAULT_CHARSET)));
        }

        Iterator<List<Message>> splitter=new MsgSplitter(messages);
        while(splitter.hasNext()){
            List<Message> next = splitter.next();
            producer.send(next);
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("消费者测试")
    public static class ConsumerTest {

        private DefaultMQPushConsumer consumer;

        @BeforeEach
        public void consumerBuilder() throws MQClientException {

            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("DEFAULT_GROUP");
            consumer.setNamesrvAddr("101.34.74.116:9876");
            this.consumer = consumer;
        }

        @AfterEach
        public void consumerStart() throws MQClientException {
            this.consumer.start();
            while (true) {
            }
        }

        @Test
        @DisplayName("消费消息")
        public void consumeMsg() throws MQClientException {

            //订阅指定的topic和tag消息，第二个参数是tag的模糊匹配，*表示订阅全部，也支持tag1 || tag2 || tag3格式
            consumer.subscribe("DelayMsg", "tagB");
            //并发地监听消息，消息将会并发接收
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                    System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
                    msgs.stream().forEach(messageExt -> {
                        System.out.println(new String(messageExt.getBody()) + "，接收时间:" + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date()));
                    });
                    // 标记该消息已经被成功消费
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });

        }

        @Test
        @DisplayName("顺序消费消息")
        public void consumeMsgOrderly() throws MQClientException {
            consumer.subscribe("OrderMsg", "tagC");
            //设置第一次启动的时候是从队列头还是队列尾开始消费，非第一次启动的话则是从上次消费的位置继续往下消费，这里设置从头到尾消费
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.registerMessageListener(new MessageListenerOrderly() {
                @Override
                public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                    //自动提交事务
                    context.setAutoCommit(true);
                    msgs.stream().forEach(msg -> {
                        System.out.println("consumeThread=" + Thread.currentThread().getName() + "  queueId=" + msg.getQueueId() + ", content:" + new String(msg.getBody()));
                    });
                    return ConsumeOrderlyStatus.SUCCESS;
                }
            });

        }


    }


}
