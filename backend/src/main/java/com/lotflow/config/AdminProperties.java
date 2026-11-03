package com.lotflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 初始化管理员账号，普通注册接口不能创建管理员。 */
@ConfigurationProperties("lotflow.admin")
public record AdminProperties(String phone, String password, String nickname) {
}
