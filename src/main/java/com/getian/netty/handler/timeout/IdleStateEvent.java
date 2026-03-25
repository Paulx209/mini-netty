package com.getian.netty.handler.timeout;

/**
 * 空闲状态事件
 * 当连接在指定时间内没有 I/O 活动时 IdleStateHandler会触发此事件
 * 该事件会作为用户事件（user event）传递给 Pipeline 中的下一个 Handler。
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class IdleStateEvent {
    /**
     * 读空闲事件（第一次）
     */
    public static final IdleStateEvent FIRST_READER_IDLE_STATE_EVENT
            = new IdleStateEvent(IdleState.READER_IDLE, true);


    /**
     * 读空闲事件
     */
    public static final IdleStateEvent READER_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.READER_IDLE, false);


    /**
     * 写空闲事件（第一次）
     */
    public static final IdleStateEvent FIRST_WRITER_IDLE_STATE_EVENT =
            new IdleStateEvent(IdleState.WRITER_IDLE, true);

    /**
     * 写空闲事件
     */
    public static final IdleStateEvent WRITER_IDLE_STATE_EVENT =
            new IdleStateEvent(IdleState.WRITER_IDLE, false);

    /**
     * 全部空闲事件（第一次）
     */
    public static final IdleStateEvent FIRST_ALL_IDLE_STATE_EVENT =
            new IdleStateEvent(IdleState.ALL_IDLE, true);

    /**
     * 全部空闲事件
     */
    public static final IdleStateEvent ALL_IDLE_STATE_EVENT =
            new IdleStateEvent(IdleState.ALL_IDLE, false);

    /**
     * 空闲状态类型
     */
    private final IdleState state;

    /**
     * 是否第一次空闲
     */
    private final boolean first;

    /**
     * 构造函数
     * @param state 空闲状态类型
     * @param first 是否第一次空闲
     */
    protected IdleStateEvent(IdleState state, boolean first) {
        this.state = state;
        this.first = first;
    }

    /**
     * 获取空闲状态类型
     *
     * @return 空闲状态类型
     */
    public IdleState state() {
        return state;
    }


    /**
     * 是否是第一次空闲
     *
     * <p>可用于区分是首次空闲还是持续空闲。
     * 首次空闲可能需要特殊处理，如发送第一个心跳。
     *
     * @return 如果是第一次空闲返回 true
     */
    public boolean isFirst() {
        return first;
    }


    @Override
    public String toString() {
        return "IdleStateEvent(" + state + (first ? ", first" : "") + ")";
    }

}
