/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jwt.primer.command;

import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.github.rholder.retry.*;
import com.github.toastshaman.dropwizard.auth.jwt.hmac.HmacSHA512Signer;
import com.github.toastshaman.dropwizard.auth.jwt.model.JsonWebToken;
import io.appform.core.hystrix.CommandFactory;
import io.jwt.primer.aeroapike.AerospikeConnectionManager;
import io.jwt.primer.config.AerospikeConfig;
import io.jwt.primer.config.JwtConfig;
import io.jwt.primer.exception.PrimerException;
import io.jwt.primer.model.*;
import io.jwt.primer.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

/**
 * @author phaneesh
 */
public interface PrimerCommands {

    Logger log = LoggerFactory.getLogger("PrimerCommands");

    Retryer<TokenClearResponse> clearRetryer = RetryerBuilder.<TokenClearResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<TokenDisableResponse> disableDynamicRetryer = RetryerBuilder.<TokenDisableResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<StaticTokenResponse> disableStaticRetryer = RetryerBuilder.<StaticTokenResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<TokenExpireResponse> expireDynamicRetryer = RetryerBuilder.<TokenExpireResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<TokenResponse> generateDynamicRetryer = RetryerBuilder.<TokenResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<StaticTokenResponse> generateStaticRetryer = RetryerBuilder.<StaticTokenResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<DynamicToken> getDynamicRetryer = RetryerBuilder.<DynamicToken>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<StaticToken> getStaticRetryer = RetryerBuilder.<StaticToken>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();

    Retryer<RefreshResponse> refreshDynamicRetryer = RetryerBuilder.<RefreshResponse>newBuilder()
            .retryIfExceptionOfType(RuntimeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .build();


    static TokenClearResponse clearDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<TokenClearResponse> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final boolean result = AerospikeConnectionManager.getClient().delete(null, key);
            return result ? TokenClearResponse.builder().userId(id).build() : null;
        };
        try {
            return CommandFactory.<TokenClearResponse>create("Dynamic", "Clear")
                    .executor(() -> clearRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static TokenDisableResponse disableDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<TokenDisableResponse> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final Record record = AerospikeConnectionManager.getClient().get(null, key, "token", "subject");
            if (null == record) {
                return null;
            }
            final Bin enabledBin = new Bin("enabled", false);
            AerospikeConnectionManager.getClient().operate(null, key, Operation.put(enabledBin));
            return TokenDisableResponse.builder()
                    .token(record.getString("token"))
                    .userId(record.getString("subject"))
                    .build();

        };
        try {
            return CommandFactory.<TokenDisableResponse>create("Dynamic", "Disable")
                    .executor(() -> disableDynamicRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static StaticTokenResponse disableStatic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<StaticTokenResponse> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_static_tokens", app), id);
            final Record record = AerospikeConnectionManager.getClient().get(null, key, "token");
            if (null == record) {
                return null;
            }
            final Bin enabledBin = new Bin("enabled", false);
            AerospikeConnectionManager.getClient().operate(null, key, Operation.put(enabledBin));
            return StaticTokenResponse.builder()
                    .token(record.getString("token"))
                    .build();
        };
        try {
            return CommandFactory.<StaticTokenResponse>create("Static", "Disable")
                    .executor(() -> disableStaticRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();

        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static TokenExpireResponse expireDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<TokenExpireResponse> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final Record record = AerospikeConnectionManager.getClient().get(null, key, "token", "subject", "expires_at");
            if (null == record) {
                return null;
            }
            long expiry = Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond();
            final Bin expiresAt = new Bin("expires_at", expiry);
            AerospikeConnectionManager.getClient().operate(null, key, Operation.put(expiresAt));
            return TokenExpireResponse.builder()
                    .token(record.getString("token"))
                    .userId(record.getString("subject"))
                    .expiry(expiry)
                    .build();
        };
        try {
            return CommandFactory.<TokenExpireResponse>create("Dynamic", "Expire")
                    .executor(() -> expireDynamicRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static TokenResponse generateDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id,
                                         final ServiceUser user, final JwtConfig jwtConfig,
                                         final HmacSHA512Signer signer) throws PrimerException {
        Callable<TokenResponse> callable = () -> {
            final JsonWebToken token = TokenUtil.token(app, id, user, jwtConfig);
            final String signedToken = signer.sign(token);
            final String refreshToken = TokenUtil.refreshToken(app, id, jwtConfig, token);
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final Bin subjectBin = new Bin("subject", user.getId());
            final Bin roleBin = new Bin("role", user.getRole());
            final Bin nameBin = new Bin("name", user.getName());
            final Bin tokenBin = new Bin("token", signedToken);
            final Bin refreshTokenBin = new Bin("refresh_token", refreshToken);
            final Bin issuedAtBin = new Bin("issued_at", token.claim().issuedAt());
            final Bin expiresAtBin = new Bin("expires_at", token.claim().expiration());
            final Bin enabledBin = new Bin("enabled", true);
            AerospikeConnectionManager.getClient().put(null, key, subjectBin, roleBin, nameBin, tokenBin, refreshTokenBin,
                    issuedAtBin, expiresAtBin, enabledBin);
            return TokenResponse.builder()
                    .token(signedToken)
                    .refreshToken(refreshToken)
                    .expiresAt(token.claim().expiration())
                    .build();
        };
        try {
            return CommandFactory.<TokenResponse>create("Dynamic", "Generate")
                    .executor(() -> generateDynamicRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch(Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static StaticTokenResponse generateStatic(final AerospikeConfig aerospikeConfig, final String app, final String id,
                                              final String role,
                                              final HmacSHA512Signer signer) throws PrimerException {
        Callable<StaticTokenResponse> callable = () -> {
            final JsonWebToken token = TokenUtil.token(app, id, role);
            final String signedToken = signer.sign(token);
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_static_tokens", app), id);
            final Bin subjectBin = new Bin("subject", id);
            final Bin roleBin = new Bin("role", role);
            final Bin tokenBin = new Bin("token", signedToken);
            final Bin issuedAtBin = new Bin("issued_at", token.claim().issuedAt());
            final Bin expiresAtBin = new Bin("expires_at", token.claim().expiration());
            final Bin enabledBin = new Bin("enabled", true);
            AerospikeConnectionManager.getClient().put(null, key, subjectBin, roleBin, tokenBin, issuedAtBin,
                    expiresAtBin, enabledBin);
            return StaticTokenResponse.builder()
                    .token(signedToken)
                    .build();
        };
        try {
            return CommandFactory.<StaticTokenResponse>create("Static", "Generate")
                    .executor(() -> generateStaticRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static DynamicToken getDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<DynamicToken> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final Record record = AerospikeConnectionManager.getClient().get(null, key);
            if (null == record) {
                return null;
            }
            return DynamicToken.builder()
                    .subject(record.getString("subject"))
                    .enabled(record.getBoolean("enabled"))
                    .expiresAt(Date.from(Instant.ofEpochSecond(record.getLong("expires_at"))))
                    .id(id)
                    .token(record.getString("token"))
                    .previousToken(record.getString("tokenp"))
                    .issuedAt(Date.from(Instant.ofEpochSecond(record.getLong("issued_at"))))
                    .name(record.getString("name"))
                    .refreshToken(record.getString("refresh_token"))
                    .previousRefreshToken(record.getString("refresh_tokenp"))
                    .role(record.getString("role"))
                    .build();
        };
        try {
            return CommandFactory.<DynamicToken>create("Dynamic", "Get")
                    .executor(() -> getDynamicRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static StaticToken getStatic(final AerospikeConfig aerospikeConfig, final String app, final String id) throws PrimerException {
        Callable<StaticToken> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_static_tokens", app), id);
            final Record record = AerospikeConnectionManager.getClient().get(null, key);
            if (null == record) {
                return null;
            }
            return StaticToken.builder()
                    .id(id)
                    .enabled(record.getBoolean("enabled"))
                    .role(record.getString("role"))
                    .subject(record.getString("subject"))
                    .token(record.getString("token"))
                    .build();
        };
        try {
            return CommandFactory.<StaticToken>create("Static", "Get")
                    .executor(() -> getStaticRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

    static RefreshResponse refreshDynamic(final AerospikeConfig aerospikeConfig, final String app, final String id,
                                          final DynamicToken token, final JwtConfig jwtConfig,
                                          final HmacSHA512Signer signer) throws PrimerException {
        Callable<RefreshResponse> callable = () -> {
            final Key key = new Key(aerospikeConfig.getNamespace(), String.format("%s_tokens", app), id);
            final ServiceUser serviceUser = ServiceUser.builder()
                    .id(token.getSubject())
                    .role(token.getRole())
                    .name(token.getName())
                    .build();
            final JsonWebToken newToken = TokenUtil.token(app, id, serviceUser, jwtConfig);
            final String newRefreshToken = TokenUtil.refreshToken(app, id, jwtConfig, newToken);
            final String newSignedToken = signer.sign(newToken);
            final Bin tokenBin = new Bin("token", newSignedToken);
            final Bin refreshTokenBin = new Bin("refresh_token", newRefreshToken);
            final Bin issuedAtBin = new Bin("issued_at", newToken.claim().issuedAt());
            final Bin expiresAtBin = new Bin("expires_at", newToken.claim().expiration());
            final Bin previousRefreshToken = new Bin("refresh_tokenp", token.getRefreshToken());
            final Bin previousToken = new Bin("tokenp", token.getToken());
            AerospikeConnectionManager.getClient().operate(null, key,
                    Operation.put(tokenBin),
                    Operation.put(previousToken),
                    Operation.put(refreshTokenBin),
                    Operation.put(previousRefreshToken),
                    Operation.put(issuedAtBin),
                    Operation.put(expiresAtBin));
            return RefreshResponse.builder()
                    .token(newSignedToken)
                    .refreshToken(newRefreshToken)
                    .expiresAt(newToken.claim().expiration())
                    .build();
        };
        try {
            return CommandFactory.<RefreshResponse>create("Dynamic", "Refresh")
                    .executor(() -> refreshDynamicRetryer.call(callable))
                    .toObservable()
                    .toBlocking()
                    .single();
        } catch (Exception e) {
            throw new PrimerException(500, "PR000", e.getMessage());
        }
    }

}
