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
package org.sonar.server.component.index;

import java.util.Arrays;
import java.util.Collection;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentUpdateDto;
import org.sonar.db.es.EsQueueDto;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.es.IndexingResult;
import org.sonar.server.es.ProjectIndexer;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.sonar.api.resources.Qualifiers.PROJECT;
import static org.sonar.server.component.index.ComponentIndexDefinition.FIELD_NAME;
import static org.sonar.server.component.index.ComponentIndexDefinition.TYPE_COMPONENT;
import static org.sonar.server.es.ProjectIndexer.Cause.PROJECT_CREATION;
import static org.sonar.server.es.ProjectIndexer.Cause.PROJECT_DELETION;
import static org.sonar.server.es.newindex.DefaultIndexSettingsElement.SORTABLE_ANALYZER;

public class ComponentIndexerTest {

  private System2 system2 = System2.INSTANCE;

  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public DbTester db = DbTester.create(system2);

  private DbClient dbClient = db.getDbClient();
  private DbSession dbSession = db.getSession();
  private ComponentIndexer underTest = new ComponentIndexer(db.getDbClient(), es.client());

  @Test
  public void test_getIndexTypes() {
    assertThat(underTest.getIndexTypes()).containsExactly(TYPE_COMPONENT);
  }

  @Test
  public void indexOnStartup_does_nothing_if_no_projects() {
    underTest.indexOnStartup(emptySet());

    assertThatIndexHasSize(0);
  }

  @Test
  public void indexOnStartup_indexes_all_components() {
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();

    underTest.indexOnStartup(emptySet());

    assertThatIndexContainsOnly(project1, project2);
  }

  @Test
  public void indexOAll_indexes_all_components() {
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();

    underTest.indexAll();

    assertThatIndexContainsOnly(project1, project2);
  }

  @Test
  public void map_fields() {
    ComponentDto project = db.components().insertPrivateProject();

    underTest.indexOnStartup(emptySet());

    assertThatIndexContainsOnly(project);
    ComponentDoc doc = es.getDocuments(TYPE_COMPONENT, ComponentDoc.class).get(0);
    assertThat(doc.getId()).isEqualTo(project.uuid());
    assertThat(doc.getKey()).isEqualTo(project.getKey());
    assertThat(doc.getProjectUuid()).isEqualTo(project.branchUuid());
    assertThat(doc.getName()).isEqualTo(project.name());
  }

  @Test
  public void indexOnStartup_does_not_index_non_main_branches() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("feature/foo"));

    underTest.indexOnStartup(emptySet());

    assertThatIndexContainsOnly(project);
  }

  @Test
  public void indexOnAnalysis_indexes_project() {
    ComponentDto project = db.components().insertPrivateProject();

    underTest.indexOnAnalysis(project.uuid());

    assertThatIndexContainsOnly(project);
  }

  @Test
  public void indexOnAnalysis_indexes_new_components() {
    ComponentDto project = db.components().insertPrivateProject();
    underTest.indexOnAnalysis(project.uuid());
    assertThatIndexContainsOnly(project);

    underTest.indexOnAnalysis(project.uuid());
    assertThatIndexContainsOnly(project);
  }

  @Test
  public void indexOnAnalysis_updates_index_on_changes() {
    ComponentDto project = db.components().insertPrivateProject();
    underTest.indexOnAnalysis(project.uuid());
    assertThatComponentHasName(project, project.name());

    // modify
    project.setName("NewName");
    updateDb(project);

    // verify that index is updated
    underTest.indexOnAnalysis(project.uuid());
    assertThatIndexContainsOnly(project);
    assertThatComponentHasName(project, "NewName");
  }

  @Test
  public void indexOnAnalysis_does_not_index_non_main_branches() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto branch = db.components().insertProjectBranch(project, b -> b.setKey("feature/foo"));

    underTest.indexOnAnalysis(branch.uuid());

    assertThatIndexHasSize(0);
  }

  @Test
  public void do_not_update_index_on_project_tag_update() {
    ComponentDto project = db.components().insertPrivateProject();

    indexProject(project, ProjectIndexer.Cause.PROJECT_TAGS_UPDATE);

    assertThatIndexHasSize(0);
  }

  @Test
  public void do_not_update_index_on_permission_change() {
    ComponentDto project = db.components().insertPrivateProject();

    indexProject(project, ProjectIndexer.Cause.PERMISSION_CHANGE);

    assertThatIndexHasSize(0);
  }

  @Test
  public void update_index_on_project_creation() {
    ComponentDto project = db.components().insertPrivateProject();

    IndexingResult result = indexProject(project, PROJECT_CREATION);

    assertThatIndexContainsOnly(project);
    assertThat(result.getTotal()).isOne();
    assertThat(result.getSuccess()).isOne();
  }

  @Test
  public void delete_some_components() {
    ComponentDto project = db.components().insertPrivateProject();
    indexProject(project, PROJECT_CREATION);

    underTest.delete(project.uuid(), emptySet());

    assertThatIndexContainsOnly(project);
  }

  @Test
  public void delete_project() {
    ComponentDto project = db.components().insertPrivateProject();
    indexProject(project, PROJECT_CREATION);
    assertThatIndexHasSize(1);

    db.getDbClient().purgeDao().deleteProject(db.getSession(), project.uuid(), PROJECT, project.name(), project.getKey());
    indexProject(project, PROJECT_DELETION);

    assertThatIndexHasSize(0);
  }

  @Test
  public void errors_during_indexing_are_recovered() {
    ComponentDto project1 = db.components().insertPrivateProject();
    es.lockWrites(TYPE_COMPONENT);

    IndexingResult result = indexProject(project1, PROJECT_CREATION);
    assertThat(result.getTotal()).isOne();
    assertThat(result.getFailures()).isOne();

    // index is still read-only, fail to recover
    result = recover();
    assertThat(result.getTotal()).isOne();
    assertThat(result.getFailures()).isOne();
    assertThat(es.countDocuments(TYPE_COMPONENT)).isZero();

    es.unlockWrites(TYPE_COMPONENT);

    result = recover();
    assertThat(result.getTotal()).isOne();
    assertThat(result.getFailures()).isZero();
    assertThatIndexContainsOnly(project1);
  }

  private IndexingResult indexProject(ComponentDto project, ProjectIndexer.Cause cause) {
    DbSession dbSession = db.getSession();
    Collection<EsQueueDto> items = underTest.prepareForRecovery(dbSession, singletonList(project.uuid()), cause);
    dbSession.commit();
    return underTest.index(dbSession, items);
  }

  private void updateDb(ComponentDto component) {
    ComponentUpdateDto updateComponent = ComponentUpdateDto.copyFrom(component);
    updateComponent.setBChanged(true);
    dbClient.componentDao().update(dbSession, updateComponent, component.qualifier());
    dbClient.componentDao().applyBChangesForBranchUuid(dbSession, component.branchUuid());
    dbSession.commit();
  }

  private IndexingResult recover() {
    Collection<EsQueueDto> items = db.getDbClient().esQueueDao().selectForRecovery(db.getSession(), System.currentTimeMillis() + 1_000L, 10);
    return underTest.index(db.getSession(), items);
  }

  private void assertThatIndexHasSize(int expectedSize) {
    assertThat(es.countDocuments(TYPE_COMPONENT)).isEqualTo(expectedSize);
  }

  private void assertThatIndexContainsOnly(ComponentDto... expectedComponents) {
    assertThat(es.getIds(TYPE_COMPONENT)).containsExactlyInAnyOrder(
      Arrays.stream(expectedComponents).map(ComponentDto::uuid).toArray(String[]::new));
  }

  private void assertThatComponentHasName(ComponentDto component, String expectedName) {
    SearchHit[] hits = es.client()
      .search(EsClient.prepareSearch(TYPE_COMPONENT.getMainType())
        .source(new SearchSourceBuilder()
          .query(matchQuery(SORTABLE_ANALYZER.subField(FIELD_NAME), expectedName))))
      .getHits()
      .getHits();
    assertThat(hits)
      .extracting(SearchHit::getId)
      .contains(component.uuid());
  }
}
