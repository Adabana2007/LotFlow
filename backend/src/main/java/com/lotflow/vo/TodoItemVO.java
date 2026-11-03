package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 由一个真实业务截止时间驱动的工作台待办。 */
@Getter
@Setter
public class TodoItemVO {
    private String type;
    private String resourceId;
    private String title;
    private String description;
    private String route;
    private OffsetDateTime dueAt;
}
