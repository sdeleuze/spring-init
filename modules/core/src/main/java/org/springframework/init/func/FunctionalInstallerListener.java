/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.init.func;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class FunctionalInstallerListener implements SmartApplicationListener {

	private static final Log logger = LogFactory.getLog(FunctionalInstallerListener.class);

	// TODO: make this class stateless
	private Collection<ApplicationContextInitializer<GenericApplicationContext>> initializers = new LinkedHashSet<>();

	private Set<Class<? extends ApplicationContextInitializer<?>>> added = new LinkedHashSet<>();

	private Object monitor = new Object();

	private static String MONITOR_NAME = FunctionalInstallerListener.class.getName() + ".MONITOR";

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationContextInitializedEvent.class.isAssignableFrom(eventType)
				|| ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationContextInitializedEvent) {
			ApplicationContextInitializedEvent initialized = (ApplicationContextInitializedEvent) event;
			ConfigurableApplicationContext context = initialized.getApplicationContext();
			if (!(context instanceof GenericApplicationContext)) {
				throw new IllegalStateException("ApplicationContext must be a GenericApplicationContext");
			}
			if (!isEnabled(context.getEnvironment())) {
				return;
			}
			if (isPresent(context)) {
				return;
			}
			GenericApplicationContext generic = (GenericApplicationContext) context;
			ConditionService conditions = initialize(generic);
			functional(generic, conditions);
			apply(generic, initialized.getSpringApplication(), conditions);
		} else if (event instanceof ApplicationEnvironmentPreparedEvent) {
			ApplicationEnvironmentPreparedEvent prepared = (ApplicationEnvironmentPreparedEvent) event;
			if (!isEnabled(prepared.getEnvironment())) {
				return;
			}
			logger.info("Preparing application context");
			SpringApplication application = prepared.getSpringApplication();
			WebApplicationType type = getWebApplicationType(application, prepared.getEnvironment());
			Class<?> contextType = getApplicationContextType(application);
			if (type == WebApplicationType.NONE) {
				if (contextType == AnnotationConfigApplicationContext.class || contextType == null) {
					application.setApplicationContextClass(GenericApplicationContext.class);
				}
			} else if (type == WebApplicationType.REACTIVE) {
				if (contextType == AnnotationConfigReactiveWebApplicationContext.class || contextType == null) {
					application.setApplicationContextClass(ReactiveWebServerApplicationContext.class);
				}
			} else if (type == WebApplicationType.SERVLET) {
				if (contextType == AnnotationConfigServletWebServerApplicationContext.class || contextType == null) {
					application.setApplicationContextClass(ServletWebServerApplicationContext.class);
				}
			}
		}
	}

	private boolean isPresent(ConfigurableApplicationContext context) {
		ConfigurableListableBeanFactory beans = InfrastructureUtils.getContext(context.getBeanFactory())
				.getBeanFactory();
		if (!beans.containsSingleton(MONITOR_NAME)) {
			beans.registerSingleton(MONITOR_NAME, monitor);
			return false;
		}
		return true;
	}

	private WebApplicationType getWebApplicationType(SpringApplication application,
			ConfigurableEnvironment environment) {
		if (environment.getProperty("spring.main.web-application-type") != null) {
			// Environment hasn't been bound to SpringApplication yet so if this is set we
			// won't know it
			return WebApplicationType
					.valueOf(environment.getProperty("spring.main.web-application-type").toUpperCase());
		}
		return application.getWebApplicationType();
	}

	private void findInitializers(GenericApplicationContext beans, SpringApplication application) {
		TypeService types = InfrastructureUtils.getBean(beans.getBeanFactory(), TypeService.class);
		for (Object source : application.getAllSources()) {
			if (source instanceof Class<?>) {
				Class<?> type = (Class<?>) source;
				String cls = type.getName().replace("$", "_") + "Initializer";
				if (types.isPresent(cls)) {
					@SuppressWarnings("unchecked")
					Class<? extends ApplicationContextInitializer<?>> initializer = (Class<? extends ApplicationContextInitializer<?>>) types
							.getType(cls);
					addInitializer(beans, initializer);
					remove(application, source);
				}
			}
		}
		if (application.getAllSources().isEmpty()) {
			// Spring Boot is fussy and doesn't like to run with no sources
			application.addPrimarySources(Arrays.asList(Object.class));
		}
	}

	private void remove(SpringApplication application, Object source) {
		Field field = ReflectionUtils.findField(SpringApplication.class, "primarySources");
		ReflectionUtils.makeAccessible(field);
		@SuppressWarnings("unchecked")
		Set<Object> sources = (Set<Object>) ReflectionUtils.getField(field, application);
		sources.remove(source);
		application.getSources().remove(source);
	}

	private Class<?> getApplicationContextType(SpringApplication application) {
		Field field = ReflectionUtils.findField(SpringApplication.class, "applicationContextClass");
		ReflectionUtils.makeAccessible(field);
		try {
			return (Class<?>) ReflectionUtils.getField(field, application);
		} catch (Exception e) {
			return null;
		}
	}

	private boolean isEnabled(ConfigurableEnvironment environment) {
		return environment.getProperty("spring.functional.enabled", Boolean.class, true);
	}

	private void functional(GenericApplicationContext context, ConditionService conditions) {
		// TODO: it would be better not to have to do this
		context.registerBean(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME,
				SlimConfigurationClassPostProcessor.class, () -> new SlimConfigurationClassPostProcessor());
		AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
	}

	public static ConditionService initialize(GenericApplicationContext context) {
		if (!InfrastructureUtils.containsBean(context.getBeanFactory(), ImportRegistrars.class.getName())) {
			GenericApplicationContext infrastructure = InfrastructureUtils.getContext(context.getBeanFactory());
			if (!InfrastructureUtils.containsBean(context.getBeanFactory(), MetadataReaderFactory.class)) {
				infrastructure.getBeanFactory().registerSingleton(MetadataReaderFactory.class.getName(),
						new CachingMetadataReaderFactory(context.getClassLoader()));
			}
			if (!InfrastructureUtils.containsBean(context.getBeanFactory(), ConditionService.class)) {
				SimpleConditionService conditions = new SimpleConditionService(context, context,
						context.getEnvironment(), context);
				infrastructure.getBeanFactory().registerSingleton(ConditionService.class.getName(), conditions);
			}
			FunctionalInstallerImportRegistrars registrar = new FunctionalInstallerImportRegistrars(context);
			infrastructure.getBeanFactory().registerSingleton(ImportRegistrars.class.getName(), registrar);
			// This one is a post processor of the main context...
			context.getBeanFactory().registerSingleton(FunctionalInstallerPostProcessor.class.getName(),
					new FunctionalInstallerPostProcessor(context));
		}
		return InfrastructureUtils.getBean(context.getBeanFactory(), ConditionService.class);
	}

	private void apply(GenericApplicationContext context) {
		List<ApplicationContextInitializer<GenericApplicationContext>> initializers = new ArrayList<>();
		for (ApplicationContextInitializer<GenericApplicationContext> result : this.initializers) {
			initializers.add(result);
		}
		OrderComparator.sort(initializers);
		if (logger.isDebugEnabled()) {
			logger.debug("Applying initializers: " + initializers);
		}
		for (ApplicationContextInitializer<GenericApplicationContext> initializer : initializers) {
			initializer.initialize(context);
		}
	}

	private void apply(GenericApplicationContext context, SpringApplication application, ConditionService conditions) {
		findInitializers(context, application);
		apply(context);
	}

	@SuppressWarnings("unchecked")
	private void addInitializer(GenericApplicationContext beans,
			Class<? extends ApplicationContextInitializer<?>> type) {
		if (type == null || this.added.contains(type)) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Adding initializer: " + type);
		}
		this.added.add(type);
		initializers.add((ApplicationContextInitializer<GenericApplicationContext>) InfrastructureUtils
				.getOrCreate(beans, type));
	}

	public static void invokeAwareMethods(Object target, Environment environment, ResourceLoader resourceLoader,
			BeanDefinitionRegistry registry) {

		if (target instanceof Aware) {
			if (target instanceof BeanClassLoaderAware) {
				ClassLoader classLoader = (registry instanceof ConfigurableBeanFactory
						? ((ConfigurableBeanFactory) registry).getBeanClassLoader()
						: resourceLoader.getClassLoader());
				if (classLoader != null) {
					((BeanClassLoaderAware) target).setBeanClassLoader(classLoader);
				}
			}
			if (target instanceof BeanFactoryAware && registry instanceof BeanFactory) {
				((BeanFactoryAware) target).setBeanFactory((BeanFactory) registry);
			}
			if (target instanceof EnvironmentAware) {
				((EnvironmentAware) target).setEnvironment(environment);
			}
			if (target instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) target).setResourceLoader(resourceLoader);
			}
		}
	}

}

class SlimConfigurationClassPostProcessor
		implements BeanDefinitionRegistryPostProcessor, BeanClassLoaderAware, PriorityOrdered {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
	}

}
