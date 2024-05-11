package me.regadpole.plumbot.bot;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.event.qq.QQEvent;
import sdk.client.ClientFactory;
import sdk.client.impl.GroupClient;
import sdk.client.impl.MessageClient;
import sdk.client.response.*;
import sdk.config.CQConfig;
import sdk.connection.Connection;
import sdk.connection.ConnectionFactory;
import sdk.event.EventDispatchers;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.GroupDecreaseNotice;
import sdk.listener.SimpleListener;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static me.regadpole.plumbot.PlumBot.*;

public class QQBot implements Bot {

    private static CQConfig http_config;
    private GroupClient client;
    private MessageClient messageClient;
    private static QQEvent qqEvent;
    private Connection connection = null;

    @Override
    public void start() {
        qqEvent = new QQEvent();
        PlumBot.INSTANCE.getLogger().info("QQ事件监听器注册完毕");
        getScheduler().runTaskAsynchronously(() -> {
            http_config = new CQConfig(Config.getCqBotHttp(), Config.getCqBotToken(), Config.getCqBotIsAccessToken());
            client =  new ClientFactory(http_config).createGroupClient();
            messageClient =  new ClientFactory(http_config).createMessageClient();
            LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue();//使用队列传输数据
            try {
                connection = ConnectionFactory.createHttpServer(Config.getCqBotListenPort(),"/",blockingQueue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            connection.create();
            EventDispatchers dispatchers = new EventDispatchers(blockingQueue);//创建事件分发器
            dispatchers.addListener(new SimpleListener<PrivateMessage>() {//私聊监听
                @Override
                public void onMessage(PrivateMessage privateMessage) {
                    qqEvent.onFriendMessageReceive(privateMessage);
                }
            });
            dispatchers.addListener(new SimpleListener<GroupMessage>() {//群聊消息监听
                @Override
                public void onMessage(GroupMessage groupMessage) {
                    List<Long> groups = Config.getGroupQQs();
                    for (long groupID : groups) {
                        if (groupID == groupMessage.getGroupId()) {
                            qqEvent.onGroupMessageReceive(groupMessage);
                        }
                    }
                }
            });
            dispatchers.addListener(new SimpleListener<GroupDecreaseNotice>() {//群聊人数减少监听
                @Override
                public void onMessage(GroupDecreaseNotice groupDecreaseNotice) {
                    List<Long> groups = Config.getGroupQQs();
                    for (long groupID : groups) {
                        if (groupID == groupDecreaseNotice.getGroupId()) {
                            qqEvent.onGroupDecreaseNotice(groupDecreaseNotice);
                        }
                    }
                }
            });
            dispatchers.start(10);//线程组处理任务
            List<Long> groups = Config.getGroupQQs();
            for (long groupID : groups) {
                PlumBot.getBot().sendMsg(true, "服务器已开启 进来玩耍吧", groupID);
            }
        });
    }

    @Override
    public void shutdown() {
        QQBot bot = (QQBot) PlumBot.getBot();
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            bot.sendGroupMsg( "正在关闭服务器", groupID);
        }
        connection.stop();
    }

    @Override
    public void sendMsg(boolean isGroup, String msg, long id) {
        if (id == 0L) {return;}
        if ("".equals(msg)) {return;}
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            if (isGroup) {
                this.sendGroupMsg(msg, id);
            } else {
                this.sendPrivateMsg(msg, id);
            }
        });
    }

    public void sendCQMsg(boolean isGroup, String msg, long id) {
        if (id == 0L) {return;}
        if ("".equals(msg)) {return;}
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            if (isGroup) {
                this.sendGroupCQMsg(msg, id);
            } else {
                this.sendPrivateCQMsg(msg, id);
            }
        });
    }

    /**
     * 发送私聊消息
     */
    public void sendPrivateMsg(String msg, long userID){
        messageClient.sendPrivateMsg(userID, msg);
    }

    /**
     * 发送群聊消息
     */
    public void sendGroupMsg(String msg, long groupId) {
        messageClient.sendGroupMsg(groupId, msg);
    }

    /**
     * 发送私聊消息
     */
    public void sendPrivateCQMsg(String msg, long userID){
        messageClient.sendPrivateMsg(userID, msg, false);
    }

    /**
     * 发送群聊消息
     */
    public void sendGroupCQMsg(String msg, long groupId) {
        messageClient.sendGroupMsg(groupId, msg, false);
    }

//    /**
//     * 发送群聊消息 失败
//     */
//    public void sendGrouForwardMsg() {
//        ForwardNode forwardNode = new ForwardNode();
//        ForwardNode.Data data = forwardNode.getData();
//        data.setName("slcower");
//        data.setContent("消息");
//        data.setSeq("消息");
//        data.setId(1720939235);
//        data.setUin(1452683658L);
//        ArrayList<ForwardNode> nodes = new ArrayList();
//        nodes.add(forwardNode);
//        nodes.add(forwardNode);
//        client.sendGroupForwardMsg(934145943L, nodes);
//    }


    /**
     * 获取消息
     */
    public void getMsg(Integer msgId) {
        Message msg = messageClient.getMsg(msgId);
        System.out.println(msg);
    }

    /**
     * 获取转发消息
     */
    public void getForwardMsg(String msgId) {
        List<ForwardMessage> msg = messageClient.getForwardMsg(msgId);
        System.out.println(msg);
    }

    /**
     * 获取图片缓存
     */
    public void getImage(String file) {
        CQFile msg = messageClient.getImage(file);
        System.out.println(msg);
    }

    /**
     * 撤回消息
     */
    public void deleteMsg(String msg, long groupId) {
        messageClient.deleteMsg( messageClient.sendGroupMsg(groupId, msg));
    }

    /**
     * 获取群信息
     */
    public GroupInfo getGroupInfo(long groupId) {
        GroupInfo groupInfo = client.getGroupInfo(groupId);
        return groupInfo;
    }

    @Override
    public String getGroupName(long groupId) {
        return getGroupInfo(groupId).getGroupName();
    }

    @Override
    public boolean checkUserInGroup(long userId, long groupId) {
        for (GroupMemberInfo member : client.getGroupMemberList(groupId)){
            if(member.getUserId().equals(userId)){
                return true;
            }
        }
        return false;
    }
}
