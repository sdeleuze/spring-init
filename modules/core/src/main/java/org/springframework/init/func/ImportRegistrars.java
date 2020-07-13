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

package org.springframework.init.func;

import java.util.Arrays;
import java.util.Set;

import org.springframework.core.io.Resource;

/**
 * @author Dave Syer
 *
 */
public interface ImportRegistrars {

	void add(Class<?> importer, Class<?> imported);

	void add(Class<?> importer, String typeName);

	Set<Imported> getImports();

	public static class Imported {

		private Class<?> source;

		private Class<?> type;

		private Resource[] resources;

		public Imported(Class<?> source, Class<?> type) {
			this.source = source;
			this.type = type;
		}

		public Imported(Class<?> source, Resource[] resources) {
			this.source = source;
			this.resources = resources;
		}

		public Resource[] getResources() {
			return resources;
		}

		public Class<?> getSource() {
			return this.source;
		}

		public Class<?> getType() {
			return this.type;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((this.source == null) ? 0 : this.source.getName().hashCode());
			result = prime * result + ((this.type == null) ? 0 : this.type.getName().hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Imported other = (Imported) obj;
			if (this.source == null) {
				if (other.source != null)
					return false;
			} else if (!this.source.equals(other.source))
				return false;
			if (this.type == null) {
				if (other.type != null)
					return false;
			} else if (!this.type.equals(other.type))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Imported [source=" + this.source.getName()

					+ ", location="
					+ (this.type == null ? Arrays.asList(this.resources == null ? new Resource[0] : resources)
							: type.getName())
					+ "]";
		}

	}

}
