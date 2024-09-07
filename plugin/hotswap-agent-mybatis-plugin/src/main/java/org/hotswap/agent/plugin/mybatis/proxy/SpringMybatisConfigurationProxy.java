package org.hotswap.agent.plugin.mybatis.proxy;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mybatis.transformers.ConfigurationCaller;
import org.hotswap.agent.util.ReflectionHelper;

import java.lang.reflect.Method;
import java.util.*;

public class SpringMybatisConfigurationProxy {


    private static AgentLogger LOGGER = AgentLogger.getLogger(SpringMybatisConfigurationProxy.class);

    protected static Map<Object, SpringMybatisConfigurationProxy> proxiedConfigurations = new HashMap<>();

    public SpringMybatisConfigurationProxy(Object sqlSessionFactoryBean) {
        this.sqlSessionFactoryBean = sqlSessionFactoryBean;
    }

    public static SpringMybatisConfigurationProxy getWrapper(Object sqlSessionFactoryBean) {
        if (!proxiedConfigurations.containsKey(sqlSessionFactoryBean)) {
            proxiedConfigurations.put(sqlSessionFactoryBean, new SpringMybatisConfigurationProxy(sqlSessionFactoryBean));
        }
        return proxiedConfigurations.get(sqlSessionFactoryBean);
    }

    public static boolean runningBySpringMybatis() {
        return !proxiedConfigurations.isEmpty();
    }

    public static void refreshProxiedConfigurations() {
        for (SpringMybatisConfigurationProxy wrapper : proxiedConfigurations.values())
            try {
                ConfigurationCaller.setInReload(wrapper.configuration, true);
                wrapper.refreshProxiedConfiguration();
                ConfigurationCaller.setInReload(wrapper.configuration, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    public void refreshProxiedConfiguration() {
        List<Interceptor> interceptors = this.configuration.getInterceptors();
        //interceptor added twice?
        Object newSqlSessionFactory = ReflectionHelper.invoke(this.sqlSessionFactoryBean, "buildSqlSessionFactory");
        this.configuration = (Configuration) ReflectionHelper.get(newSqlSessionFactory, "configuration");
        List<Interceptor> interceptors1 = this.configuration.getInterceptors();
        //remove duplicate interceptor.

        Set<String> allInterceporNames = new HashSet<String>();
        for (Interceptor interceptor : interceptors1) {
            allInterceporNames.add(interceptor.getClass().getName());
        }
        for (Interceptor interceptor : interceptors) {
            if(!allInterceporNames.contains(interceptor.getClass().getName())){
                this.configuration.addInterceptor(interceptor);
                LOGGER.info("Add interceptor: " + interceptor.getClass().getName());
            }
        }
    }

    private Object sqlSessionFactoryBean;
    public Configuration configuration;
    private Configuration proxyInstance;

    public Configuration proxy(Configuration origConfiguration) {
        this.configuration = origConfiguration;
        if (proxyInstance == null) {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(Configuration.class);

            MethodHandler handler = new MethodHandler() {
                @Override
                public Object invoke(Object self, Method overridden, Method forwarder,
                                     Object[] args) throws Throwable {
                    return overridden.invoke(configuration, args);
                }
            };

            try {
                proxyInstance = (Configuration) factory.create(new Class[0], null, handler);
            } catch (Exception e) {
                throw new Error("Unable instantiate Configuration proxy", e);
            }
        }
        return proxyInstance;
    }

    public static boolean isMybatisEntity(Class<?> clazz) {
        for (SpringMybatisConfigurationProxy configurationProxy : proxiedConfigurations.values()) {
            if (ConfigurationCaller.isMybatisObj(configurationProxy.configuration, clazz)) {
                return true;
            }
        }

        return false;
    }


}
