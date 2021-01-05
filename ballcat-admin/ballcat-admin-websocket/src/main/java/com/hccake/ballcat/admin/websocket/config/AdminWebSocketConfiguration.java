package com.hccake.ballcat.admin.websocket.config;

import com.hccake.ballcat.admin.websocket.user.UserAttributeHandshakeInterceptor;
import com.hccake.ballcat.admin.websocket.user.UserSessionKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * @author Hccake 2021/1/5
 * @version 1.0
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebSocketConfiguration {

	@Bean
	@ConditionalOnMissingBean(UserAttributeHandshakeInterceptor.class)
	public HandshakeInterceptor authenticationHandshakeInterceptor() {
		return new UserAttributeHandshakeInterceptor();
	}

	@Bean
	@ConditionalOnMissingBean(UserSessionKeyGenerator.class)
	public UserSessionKeyGenerator userSessionKeyGenerator() {
		return new UserSessionKeyGenerator();
	}

}
