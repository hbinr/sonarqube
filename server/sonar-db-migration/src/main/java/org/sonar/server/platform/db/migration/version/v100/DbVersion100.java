/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform.db.migration.version.v100;

import org.sonar.server.platform.db.migration.step.MigrationStepRegistry;
import org.sonar.server.platform.db.migration.version.DbVersion;

// ignoring bad number formatting, as it's indented that we align the migration numbers to SQ versions
@SuppressWarnings("java:S3937")
public class DbVersion100 implements DbVersion {

  /**
   * We use the start of the 10.X cycle as an opportunity to align migration numbers with the SQ version number.
   * Please follow this pattern:
   * 10_0_000
   * 10_0_001
   * 10_0_002
   * 10_1_000
   * 10_1_001
   * 10_1_002
   * 10_2_000
   */

  @Override
  public void addSteps(MigrationStepRegistry registry) {
    registry
      .add(10_0_000, "Remove orphan rules in Quality Profiles", RemoveOrphanRulesFromQualityProfiles.class)
      .add(10_0_001, "Drop index 'projects_module_uuid' in the 'Components' table", DropIndexProjectsModuleUuidInComponents.class)
      .add(10_0_002, "Drop column 'module_uuid' in the 'Components' table", DropModuleUuidInComponents.class)
      .add(10_0_003, "Drop column 'module_uuid_path' in the 'Components' table", DropModuleUuidPathInComponents.class)
      .add(10_0_004, "Drop column 'b_module_uuid' in the 'Components' table", DropBModuleUuidInComponents.class)
      .add(10_0_005, "Drop column 'b_module_uuid_path' in the 'Components' table", DropBModuleUuidPathInComponents.class)
      .add(10_0_006, "Drop index 'projects_root_uuid' in the 'Components' table", DropIndexProjectsRootUuidInComponents.class)
      .add(10_0_007, "Drop column 'root_uuid' in the 'Components' table", DropRootUuidInComponents.class)


    ;
  }
}
