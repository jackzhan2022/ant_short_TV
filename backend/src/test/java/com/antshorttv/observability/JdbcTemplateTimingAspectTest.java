package com.antshorttv.observability;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcTemplateTimingAspectTest {
    @Test void connectionAccessAndConfigurationDoNotCountAsDatabaseStatements(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:query-observation","sa","");
        var factory=new AspectJProxyFactory(new JdbcTemplate(source));factory.setProxyTargetClass(true);factory.addAspect(new JdbcTemplateTimingAspect());
        JdbcTemplate jdbc=factory.getProxy();
        try(var scope=DatabaseQueryObservation.open()){
            assertThat(jdbc.getDataSource()).isSameAs(source);jdbc.setQueryTimeout(1);
            assertThat(DatabaseQueryObservation.snapshot()[0]).isZero();
            assertThat(jdbc.queryForObject("select 1",Integer.class)).isEqualTo(1);
            assertThat(DatabaseQueryObservation.snapshot()[0]).isEqualTo(1);
        }
    }
}
