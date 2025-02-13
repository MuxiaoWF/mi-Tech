package com.muxiao.localCaptcha;

import com.muxiao.start;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huangqingshi
 * @Date 2019-08-17
 */
@RestController
public class ShutDownController implements ApplicationContextAware {

    private ApplicationContext context;

    @PostMapping("/shut-down-context")
    public String shutDownContext() {
        start.passChallengeRef.set(new String[]{});
        return performShutdown();
    }

    @GetMapping("/shut-down-context")
    public String shutDownContextGet() {
        start.passChallengeRef.set(new String[]{});
        return performShutdown();
    }

    private String performShutdown() {
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) context;
        SpringApplication.exit(ctx, () -> 0);
        ctx.close();
        return "context is shutdown";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
}
