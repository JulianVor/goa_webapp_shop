package de.goafestival.shop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MaintenanceModeInterceptor maintenanceModeInterceptor;

    public WebConfig(MaintenanceModeInterceptor maintenanceModeInterceptor) {
        this.maintenanceModeInterceptor = maintenanceModeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceModeInterceptor)
                .excludePathPatterns("/admin/**", "/login", "/logout", "/css/**", "/js/**", "/img/**", "/fonts/**");
    }
}
