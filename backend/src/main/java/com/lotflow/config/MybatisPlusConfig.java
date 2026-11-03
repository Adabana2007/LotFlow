package com.lotflow.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** MyBatis-Plus 公共字段填充和乐观锁配置。 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // version 字段由 MyBatis-Plus 乐观锁插件参与更新，Service 仍负责业务冲突处理。
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                // 公共时间统一写成 UTC，避免 JVM 默认时区改变数据库事实。
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                strictInsertFill(metaObject, "gmtCreate", OffsetDateTime.class, now);
                strictInsertFill(metaObject, "gmtModified", OffsetDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "gmtModified", OffsetDateTime.class,
                        OffsetDateTime.now(ZoneOffset.UTC));
            }
        };
    }
}
