/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.bytecode.enhancement.lazy.proxy;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.stat.spi.StatisticsImplementor;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.bytecode.enhancement.EnhancementOptions;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrea Boriero
 * @author Gail Badner
 */
@TestForIssue(jiraKey = "HHH-11147")
@RunWith(BytecodeEnhancerRunner.class)
@EnhancementOptions(lazyLoading = true)
public class LoadANonExistingNotFoundBatchEntityTest extends BaseNonConfigCoreFunctionalTestCase {

	private static final int NUMBER_OF_ENTITIES = 20;

	@Test
	@TestForIssue(jiraKey = "HHH-11147")
	public void loadEntityWithNotFoundAssociation() {
		final StatisticsImplementor statistics = sessionFactory().getStatistics();
		statistics.clear();

		inTransaction( (session) -> {
			List<Employee> employees = new ArrayList<>( NUMBER_OF_ENTITIES );
			for ( int i = 0 ; i < NUMBER_OF_ENTITIES ; i++ ) {
				employees.add( session.load( Employee.class, i + 1 ) );
			}
			for ( int i = 0 ; i < NUMBER_OF_ENTITIES ; i++ ) {
				Hibernate.initialize( employees.get( i ) );
				assertNull( employees.get( i ).employer );
			}
		} );

		// we should get `NUMBER_OF_ENTITIES` queries
		assertEquals( NUMBER_OF_ENTITIES, statistics.getPrepareStatementCount() );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-11147")
	public void getEntityWithNotFoundAssociation() {
		final StatisticsImplementor statistics = sessionFactory().getStatistics();
		statistics.clear();

		inTransaction( (session) -> {
			for ( int i = 0 ; i < NUMBER_OF_ENTITIES ; i++ ) {
				Employee employee = session.get( Employee.class, i + 1 );
				assertNull( employee.employer );
			}
		} );

		// we should get `NUMBER_OF_ENTITIES` queries
		assertEquals( NUMBER_OF_ENTITIES, statistics.getPrepareStatementCount() );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-11147")
	public void updateNotFoundAssociationWithNew() {
		final StatisticsImplementor statistics = sessionFactory().getStatistics();
		statistics.clear();

		inTransaction( (session) -> {
			for ( int i = 0; i < NUMBER_OF_ENTITIES; i++ ) {
				Employee employee = session.get( Employee.class, i + 1 );
				Employer employer = new Employer();
				employer.id = 2 * employee.id;
				employer.name = "Employer #" + employer.id;
				employee.employer = employer;
			}
		} );

		inTransaction( (session) -> {
			for ( int i = 0; i < NUMBER_OF_ENTITIES; i++ ) {
				Employee employee = session.get( Employee.class, i + 1 );
				assertTrue( Hibernate.isInitialized( employee.employer ) );
				assertEquals( employee.id * 2, employee.employer.id );
				assertEquals( "Employer #" + employee.employer.id, employee.employer.name );
			}
		} );
	}

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] {
				Employee.class,
				Employer.class
		};
	}

	@Before
	public void setUpData() {
		doInHibernate(
				this::sessionFactory, session -> {
					for ( int i = 0; i < NUMBER_OF_ENTITIES; i++ ) {
						final Employee employee = new Employee();
						employee.id = i + 1;
						employee.name = "Employee #" + employee.id;
						session.persist( employee );
					}
				}
		);


		doInHibernate(
				this::sessionFactory, session -> {
					// Add "not found" associations
					session.createNativeQuery( "update Employee set employer_id = id" ).executeUpdate();
				}
		);
	}

	@After
	public void cleanupDate() {
		doInHibernate(
				this::sessionFactory, session -> {
					session.createQuery( "delete from Employee" ).executeUpdate();
					session.createQuery( "delete from Employer" ).executeUpdate();
				}
		);
	}

	@Override
	protected void configureStandardServiceRegistryBuilder(StandardServiceRegistryBuilder ssrb) {
		super.configureStandardServiceRegistryBuilder( ssrb );
		ssrb.applySetting( AvailableSettings.FORMAT_SQL, "false" );
		ssrb.applySetting( AvailableSettings.GENERATE_STATISTICS, "true" );
	}

	@Override
	protected void configureSessionFactoryBuilder(SessionFactoryBuilder sfb) {
		super.configureSessionFactoryBuilder( sfb );
		sfb.applyStatisticsSupport( true );
		sfb.applySecondLevelCacheSupport( false );
		sfb.applyQueryCacheSupport( false );
	}

	@Entity(name = "Employee")
	public static class Employee {
		@Id
		private int id;

		private String name;

		@ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
		@JoinColumn(name = "employer_id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
		@NotFound(action=NotFoundAction.IGNORE)
		private Employer employer;
	}

	@Entity(name = "Employer")
	@BatchSize(size = 10)
	public static class Employer {
		@Id
		private int id;

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}