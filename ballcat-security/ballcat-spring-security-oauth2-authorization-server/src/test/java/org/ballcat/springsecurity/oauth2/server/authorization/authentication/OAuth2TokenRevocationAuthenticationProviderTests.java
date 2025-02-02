/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballcat.springsecurity.oauth2.server.authorization.authentication;

import org.ballcat.springsecurity.oauth2.server.authorization.TestOAuth2Authorizations;
import org.ballcat.springsecurity.oauth2.server.authorization.client.TestRegisteredClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OAuth2TokenRevocationAuthenticationProvider}.
 *
 * @author Vivek Babu
 * @author Joe Grandja
 */
class OAuth2TokenRevocationAuthenticationProviderTests {

	private OAuth2AuthorizationService authorizationService;

	private OAuth2TokenRevocationAuthenticationProvider authenticationProvider;

	@BeforeEach
	void setUp() {
		this.authorizationService = mock(OAuth2AuthorizationService.class);
		this.authenticationProvider = new OAuth2TokenRevocationAuthenticationProvider(this.authorizationService);
	}

	@Test
	void constructorWhenAuthorizationServiceNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new OAuth2TokenRevocationAuthenticationProvider(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("authorizationService cannot be null");
	}

	@Test
	void supportsWhenTypeOAuth2TokenRevocationAuthenticationTokenThenReturnTrue() {
		assertThat(this.authenticationProvider.supports(OAuth2TokenRevocationAuthenticationToken.class)).isTrue();
	}

	@Test
	void authenticateWhenClientPrincipalNotOAuth2ClientAuthenticationTokenThenThrowOAuth2AuthenticationException() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		TestingAuthenticationToken clientPrincipal = new TestingAuthenticationToken(registeredClient.getClientId(),
				registeredClient.getClientSecret());
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken("token",
				clientPrincipal, OAuth2TokenType.ACCESS_TOKEN.getValue());
		assertThatThrownBy(() -> this.authenticationProvider.authenticate(authentication))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(ex -> ((OAuth2AuthenticationException) ex).getError())
			.extracting("errorCode")
			.isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT);
	}

	@Test
	void authenticateWhenClientPrincipalNotAuthenticatedThenThrowOAuth2AuthenticationException() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(
				registeredClient.getClientId(), ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
				registeredClient.getClientSecret(), null);
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken("token",
				clientPrincipal, OAuth2TokenType.ACCESS_TOKEN.getValue());
		assertThatThrownBy(() -> this.authenticationProvider.authenticate(authentication))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(ex -> ((OAuth2AuthenticationException) ex).getError())
			.extracting("errorCode")
			.isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT);
	}

	@Test
	void authenticateWhenInvalidTokenThenNotRevoked() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
				ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registeredClient.getClientSecret());
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken("token",
				clientPrincipal, OAuth2TokenType.ACCESS_TOKEN.getValue());
		OAuth2TokenRevocationAuthenticationToken authenticationResult = (OAuth2TokenRevocationAuthenticationToken) this.authenticationProvider
			.authenticate(authentication);
		assertThat(authenticationResult.isAuthenticated()).isFalse();
		verify(this.authorizationService, never()).save(any());
	}

	@Test
	void authenticateWhenTokenIssuedToAnotherClientThenThrowOAuth2AuthenticationException() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		OAuth2Authorization authorization = TestOAuth2Authorizations
			.authorization(TestRegisteredClients.registeredClient2().build())
			.build();
		when(this.authorizationService.findByToken(eq("token"), isNull())).thenReturn(authorization);

		OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
				ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registeredClient.getClientSecret());
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken("token",
				clientPrincipal, OAuth2TokenType.ACCESS_TOKEN.getValue());

		assertThatThrownBy(() -> this.authenticationProvider.authenticate(authentication))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(ex -> ((OAuth2AuthenticationException) ex).getError())
			.extracting("errorCode")
			.isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT);
	}

	@Test
	void authenticateWhenValidRefreshTokenThenRevoked() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		OAuth2Authorization authorization = TestOAuth2Authorizations.authorization(registeredClient).build();
		when(this.authorizationService.findByToken(eq(authorization.getRefreshToken().getToken().getTokenValue()),
				isNull()))
			.thenReturn(authorization);

		OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
				ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registeredClient.getClientSecret());
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken(
				authorization.getRefreshToken().getToken().getTokenValue(), clientPrincipal,
				OAuth2TokenType.REFRESH_TOKEN.getValue());

		OAuth2TokenRevocationAuthenticationToken authenticationResult = (OAuth2TokenRevocationAuthenticationToken) this.authenticationProvider
			.authenticate(authentication);
		assertThat(authenticationResult.isAuthenticated()).isTrue();

		ArgumentCaptor<OAuth2Authorization> authorizationCaptor = ArgumentCaptor.forClass(OAuth2Authorization.class);
		verify(this.authorizationService).save(authorizationCaptor.capture());

		OAuth2Authorization updatedAuthorization = authorizationCaptor.getValue();
		OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = updatedAuthorization.getRefreshToken();
		assertThat(refreshToken.isInvalidated()).isTrue();
		OAuth2Authorization.Token<OAuth2AccessToken> accessToken = updatedAuthorization.getAccessToken();
		assertThat(accessToken.isInvalidated()).isTrue();
	}

	@Test
	void authenticateWhenValidAccessTokenThenRevoked() {
		RegisteredClient registeredClient = TestRegisteredClients.registeredClient().build();
		OAuth2Authorization authorization = TestOAuth2Authorizations.authorization(registeredClient).build();
		when(this.authorizationService.findByToken(eq(authorization.getAccessToken().getToken().getTokenValue()),
				isNull()))
			.thenReturn(authorization);

		OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient,
				ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registeredClient.getClientSecret());
		OAuth2TokenRevocationAuthenticationToken authentication = new OAuth2TokenRevocationAuthenticationToken(
				authorization.getAccessToken().getToken().getTokenValue(), clientPrincipal,
				OAuth2TokenType.ACCESS_TOKEN.getValue());

		OAuth2TokenRevocationAuthenticationToken authenticationResult = (OAuth2TokenRevocationAuthenticationToken) this.authenticationProvider
			.authenticate(authentication);
		assertThat(authenticationResult.isAuthenticated()).isTrue();

		ArgumentCaptor<OAuth2Authorization> authorizationCaptor = ArgumentCaptor.forClass(OAuth2Authorization.class);
		verify(this.authorizationService).save(authorizationCaptor.capture());

		OAuth2Authorization updatedAuthorization = authorizationCaptor.getValue();
		OAuth2Authorization.Token<OAuth2AccessToken> accessToken = updatedAuthorization.getAccessToken();
		assertThat(accessToken.isInvalidated()).isTrue();
		OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = updatedAuthorization.getRefreshToken();
		assertThat(refreshToken.isInvalidated()).isFalse();
	}

}
