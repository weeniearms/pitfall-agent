package com.github.weeniearms.pitfall.agent;

import com.github.weeniearms.pitfall.agent.configuration.agent.AgentConfiguration;
import com.github.weeniearms.pitfall.agent.configuration.agent.AgentConfigurationParser;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.utility.JavaModule;
import okhttp3.*;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public class Agent {
    public static void premain(String args, Instrumentation instrumentation) {
        Arrays.asList(new HttpServletFailInterceptor(), new HttpServletDelayInterceptor())
                .stream()
                .forEach(i -> {
                    new AgentBuilder.Default()
                            .with(new LoggingListener())
                            .type(i.getTypeMatcher())
                            .transform((builder, type, classLoader) -> builder.method(i.getMethodMatcher())
                                    .intercept(MethodDelegation.to(i))
                            ).installOn(instrumentation);

                    registerInterceptor(i);
                });
    }

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private static void registerInterceptor(Interceptor interceptor) {
        AgentConfiguration agentConfiguration = new AgentConfigurationParser().parse();

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(JSON, "{\"test\": 123}");
        Request request = new Request.Builder()
                .url(agentConfiguration.getServer() + String.format("/groups/%s/apps/%s/interceptors", agentConfiguration.getGroup(), agentConfiguration.getName()))
                .post(body)
                .build();
        
        try {
            client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class LoggingListener implements AgentBuilder.Listener {

        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, DynamicType dynamicType) {
            System.out.println("Transformed - " + typeDescription + ", type = " + dynamicType);
        }

        @Override
        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
        }

        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, Throwable throwable) {
            System.out.println("Error - " + typeName + ", " + throwable.getMessage());
            throwable.printStackTrace();
        }

        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module) {
        }
    }
}
