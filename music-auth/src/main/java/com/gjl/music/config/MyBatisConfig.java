package com.gjl.music.config;

import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * MyBatis 全局配置 —— 在主应用模块（music-auth）中创建基础设施 Bean。
 *
 * <p>扫描 common 模块的 mapper（{@code com.gjl.music.mapper}）和
 * auth 模块的 mapper（{@code com.gjl.music.auth.mapper}）。
 * 原 music-common 中的同名类已移至此。</p>
 */
@Configuration
@MapperScan({"com.gjl.music.mapper", "com.gjl.music.auth.mapper",
        "com.gjl.music.editor.mapper", "com.gjl.music.playback.**.mapper"})
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources(
                        "classpath*:com/gjl/music/mapper/*.xml," +
                        "classpath*:com/gjl/music/auth/mapper/*.xml," +
                        "classpath*:com/gjl/music/editor/mapper/*.xml," +
                        "classpath*:com/gjl/music/playback/**/*.xml"));

        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(cfg);

        // databaseId 映射：H2 → "h2"，MySQL → "mysql"
        VendorDatabaseIdProvider dbIdProvider = new VendorDatabaseIdProvider();
        Properties props = new Properties();
        props.setProperty("H2", "h2");
        props.setProperty("MySQL", "mysql");
        dbIdProvider.setProperties(props);
        factory.setDatabaseIdProvider(dbIdProvider);

        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
