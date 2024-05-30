package com.tgf.gateway.nacos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * @Author: zqz
 * @CreateTime: 2024/05/30
 * @Description: 请求入参过滤器
 * @Version: 1.0
 */
@Component
public class WrapperRequestGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(WrapperRequestGlobalFilter.class);
    private static final String F_POST = "POST";
    private static final String F_GET = "GET";
    private static final String F_BODY = "POST_BODY";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uriPath = request.getURI();
        String path = request.getPath().value();
        String method = request.getMethodValue();
        HttpHeaders header = request.getHeaders();
        log.info("***********************************Request Info**********************************");
        log.info("Request：URI = {}, path = {}，method = {}，header = {}。", uriPath, path, method, header);
        if (F_POST.equals(method)) {
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        String bodyString = new String(bytes, StandardCharsets.UTF_8);
                        log.info("Param：{}", bodyString);
                        exchange.getAttributes().put(F_BODY, bodyString);
                        DataBufferUtils.release(dataBuffer);
                        Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
                            DataBuffer buffer = exchange.getResponse().bufferFactory()
                                    .wrap(bytes);
                            return Mono.just(buffer);
                        });

                        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return cachedFlux;
                            }
                        };
                        log.info("****************************************************************************\n");
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
        } else if (F_GET.equals(method)) {
            MultiValueMap<String, String> queryParams = request.getQueryParams();
            log.info("Param：{}", queryParams);
            log.info("****************************************************************************\n");
            return chain.filter(exchange);
        }
        log.info("****************************************************************************\n");
        return chain.filter(exchange);
    }
}
