/*
 * Copyright 2020-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;

/**
 * @author Dave Syer
 *
 */
public class InfrastructureInitializer implements ApplicationContextInitializer<GenericApplicationContext>, Ordered {

	private List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();

	private int order = Ordered.HIGHEST_PRECEDENCE + 5;

	public InfrastructureInitializer() {
	}

	public InfrastructureInitializer(ApplicationContextInitializer<?>... initializers) {
		this(Ordered.HIGHEST_PRECEDENCE, initializers);
	}

	public InfrastructureInitializer(int order, ApplicationContextInitializer<?>... initializers) {
		this.order = order;
		this.initializers.addAll(Arrays.asList(initializers));
	}

	@Override
	public void initialize(GenericApplicationContext context) {
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		if (!InfrastructureUtils.isInstalled(beanFactory)) {
			GenericApplicationContext infra = new GenericApplicationContext();
			infra.getBeanFactory().registerSingleton(TypeService.class.getName(),
					new DefaultTypeService(context.getClassLoader()));
			for (ApplicationContextInitializer<?> initializer : initializers) {
				@SuppressWarnings("unchecked")
				ApplicationContextInitializer<GenericApplicationContext> generic = (ApplicationContextInitializer<GenericApplicationContext>) initializer;
				generic.initialize(infra);
			}
			infra.refresh();
			InfrastructureUtils.install(beanFactory, infra);
		}
	}

	@Override
	public int getOrder() {
		return order;
	}

}
