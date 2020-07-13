package org.springframework.cloud.bootstrap.encrypt;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.init.func.ConditionService;
import org.springframework.init.func.ImportRegistrars;
import org.springframework.init.func.InfrastructureUtils;

public class EncryptionBootstrapConfigurationInitializer
		implements ApplicationContextInitializer<GenericApplicationContext> {

	@Override
	public void initialize(GenericApplicationContext context) {
		ConditionService conditions = InfrastructureUtils.getBean(context.getBeanFactory(), ConditionService.class);
		if (conditions.matches(EncryptionBootstrapConfiguration.class)) {
			if (context.getBeanFactory().getBeanNamesForType(EncryptionBootstrapConfiguration.class).length == 0) {
				new EncryptionBootstrapConfiguration_RsaEncryptionConfigurationInitializer().initialize(context);
				new EncryptionBootstrapConfiguration_VanillaEncryptionConfigurationInitializer().initialize(context);
				InfrastructureUtils.getBean(context.getBeanFactory(), ImportRegistrars.class).add(
						EncryptionBootstrapConfiguration.class,
						"org.springframework.boot.context.properties.EnableConfigurationPropertiesRegistrar");
				context.registerBean(EncryptionBootstrapConfiguration.class,
						() -> new EncryptionBootstrapConfiguration());
				context.registerBean("environmentDecryptApplicationListener",
						EnvironmentDecryptApplicationInitializer.class,
						() -> context.getBean(EncryptionBootstrapConfiguration.class)
								.environmentDecryptApplicationListener());
			}
		}
	}

}
