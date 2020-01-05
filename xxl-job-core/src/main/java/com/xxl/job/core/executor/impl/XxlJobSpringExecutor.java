package com.xxl.job.core.executor.impl;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.JobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * xxl-job executor (for spring)
 *
 * @author xuxueli 2018-11-01 09:24:52
 */
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, InitializingBean, DisposableBean {


    // start
    @Override
    public void afterPropertiesSet() throws Exception {

        // init JobHandler Repository
        //旧版本的使用JobHandler注解标记任务的方式，新版本已经弃用。 此处保留，只为了兼容旧版 mark by cuixy 2020年1月3日 17:10:02
        initJobHandlerRepository(applicationContext);

        // init JobHandler Repository (for method)
        //新版本采用XxlJob方法级注解来创建任务 mark by cuixy 2020年1月3日 17:12:06
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        GlueFactory.refreshInstance(1);

        // super start
        super.start();
    }

    // destroy
    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler) {
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
    }

    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler from method
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            Object bean = applicationContext.getBean(beanDefinitionName);
            Method[] methods = bean.getClass().getDeclaredMethods();
            for (Method method: methods) {
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                if (xxlJob != null) {

                    // name
                    String name = xxlJob.value();
                    if (name.trim().length() == 0) {
                        throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                    }
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }

                    // execute method
                    if (!(method.getParameterTypes()!=null && method.getParameterTypes().length==1 && method.getParameterTypes()[0].isAssignableFrom(String.class))) {
                        throw new RuntimeException("xxl-job method-jobhandler param-classtype invalid, for[" + bean.getClass() + "#"+ method.getName() +"] , " +
                                "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                    }
                    if (!method.getReturnType().isAssignableFrom(ReturnT.class)) {
                        throw new RuntimeException("xxl-job method-jobhandler return-classtype invalid, for[" + bean.getClass() + "#"+ method.getName() +"] , " +
                                "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                    }
                    method.setAccessible(true);

                    // init and destory
                    Method initMethod = null;
                    Method destroyMethod = null;

                    if(xxlJob.init().trim().length() > 0) {
                        try {
                            initMethod = bean.getClass().getDeclaredMethod(xxlJob.init());
                            initMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                        }
                    }
                    if(xxlJob.destroy().trim().length() > 0) {
                        try {
                            destroyMethod = bean.getClass().getDeclaredMethod(xxlJob.destroy());
                            destroyMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                        }
                    }

                    // registry jobhandler
                    registJobHandler(name, new MethodJobHandler(bean, method, initMethod, destroyMethod));
                }
            }
        }
    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
