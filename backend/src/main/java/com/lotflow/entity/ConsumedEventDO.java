package com.lotflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 消费者幂等记录。 */
@Getter
@Setter
@TableName("consumed_event")
public class ConsumedEventDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String consumerName;
    private String status;
    private Integer retryCount;
    private String lastError;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
