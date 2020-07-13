/*
 * Copyright 2018 the original author or authors.
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

package app.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.init.func.InfrastructureInitializer;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest
@ContextConfiguration(initializers = TestInitializer.class)
public class FunctionalApplicationTests {

	@Autowired
	private Bar bar;

	@Test
	public void test() {
		assertThat(bar).isNotNull();
		assertThat(bar.getFoo().getValue()).isNotNull();
	}

}

class TestInitializer extends InfrastructureInitializer {
	public TestInitializer() {
		super(new Initializer());
	}
}