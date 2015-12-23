/************************************************************************
 * Copyright (c) 2015 IoT-Solutions e.U.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ************************************************************************/

package test.concurrency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import iot.jcypher.concurrency.Locking;
import iot.jcypher.database.DBAccessFactory;
import iot.jcypher.database.DBProperties;
import iot.jcypher.database.DBType;
import iot.jcypher.database.IDBAccess;
import iot.jcypher.domain.DomainAccessFactory;
import iot.jcypher.domain.IDomainAccess;
import iot.jcypher.domain.internal.IIntDomainAccess;
import iot.jcypher.query.result.JcError;
import iot.jcypher.query.result.JcResultException;
import iot.jcypher.util.QueriesPrintObserver;
import iot.jcypher.util.QueriesPrintObserver.ContentToObserve;
import iot.jcypher.util.QueriesPrintObserver.QueryToObserve;
import test.AbstractTestSuite;
import test.domainquery.Population;
import test.domainquery.model.Person;
import test.domainquery.model.PoBox;
import test.domainquery.model.PointOfContact;
import util.TestDataReader;

public class ConcurrentQueryReplayTest extends AbstractTestSuite {

	public static IDBAccess dbAccess;
	public static String domainName;
	private static List<Object> storedDomainObjects;
	
	@Test
	public void replayQuery_01() {
		String qCypher;
		Locking lockingStrategy = Locking.OPTIMISTIC;
		
		/******* first client loading smith ******/
		IDomainAccess da1 = DomainAccessFactory.createDomainAccess(dbAccess,
				domainName).setLockingStrategy(lockingStrategy);
		Person a_smith = ConcurrencyTest.findPerson(da1, "Smith", "Angelina");
		Person j_smith1 = ConcurrencyTest.findPerson(da1, "Smith", "John");
		
		/******* second client loading j_smith ******/
		IDomainAccess da2 = DomainAccessFactory.createDomainAccess(dbAccess,
				domainName).setLockingStrategy(lockingStrategy);
		Person j_smith2 = ConcurrencyTest.findPerson(da2, "Smith", "John");
		
		/******* second client extending model ******/
		PoBox poBox = new PoBox();
		poBox.setNumber(12345);
		List<PointOfContact> pocs = j_smith2.getPointsOfContact();
		pocs.add(poBox);
		List<JcError> errors = da2.store(j_smith2);
		assertTrue(errors.isEmpty());
		if (errors.size() > 0) {
			printErrors(errors);
			// throw new JcResultException(errors);
		}
		String domModel2 = ((IIntDomainAccess)da2).getInternalDomainAccess().domainModelAsString();
		String domModel1 = ((IIntDomainAccess)da1).getInternalDomainAccess().domainModelAsString();
		
		assertFalse(domModel2.equals(domModel1));
		
		/******* first client performing a query ******/
		Person j_smith = ConcurrencyTest.findPerson(da1, "Smith", "John");
		
		String domModel11 = ((IIntDomainAccess)da1).getInternalDomainAccess().domainModelAsString();
		assertEquals(domModel2, domModel11);
		
		// remove model version info
//		domModel11 = domModel11.substring(domModel11.indexOf('{'), domModel11.length());
//		domModel2 = domModel2.substring(domModel2.indexOf('{'), domModel2.length());
		
		return;
	}
	
	@BeforeClass
	public static void before() {
		domainName = "QTEST-DOMAIN";
		Properties props = new Properties();
	
		// properties for remote access and for embedded access
		// (not needed for in memory access)
		props.setProperty(DBProperties.SERVER_ROOT_URI, "http://localhost:7474");
		props.setProperty(DBProperties.DATABASE_DIR, "C:/NEO4J_DBS/01");
	
		dbAccess = DBAccessFactory.createDBAccess(DBType.REMOTE, props);
		// dbAccess = DBAccessFactory.createDBAccess(DBType.REMOTE, props,
		// "neo4j", "jcypher");
	
		Population population = new Population();
	
		storedDomainObjects = population.createPopulation();
	
		QueriesPrintObserver.addOutputStream(System.out);
	
		QueriesPrintObserver.addToEnabledQueries(QueryToObserve.DOMAIN_INFO,
				ContentToObserve.CYPHER);
		QueriesPrintObserver.addToEnabledQueries(QueryToObserve.DOM_QUERY,
				ContentToObserve.CYPHER);
		QueriesPrintObserver.addToEnabledQueries(QueryToObserve.CLOSURE_QUERY,
				ContentToObserve.CYPHER);
		
		initDB(Locking.OPTIMISTIC);
	}

	@AfterClass
	public static void after() {
		if (dbAccess != null) {
			dbAccess.close();
			dbAccess = null;
		}
		storedDomainObjects = null;
		QueriesPrintObserver.removeAllEnabledQueries();
		QueriesPrintObserver.removeAllOutputStreams();
	}
	
	private static void initDB(Locking lockingStrategy) {
		List<JcError> errors = dbAccess.clearDatabase();
		if (errors.size() > 0) {
			printErrors(errors);
			throw new JcResultException(errors);
		}
		IDomainAccess da = DomainAccessFactory.createDomainAccess(dbAccess,
				domainName).setLockingStrategy(lockingStrategy);
		errors = da.store(storedDomainObjects);
		if (errors.size() > 0) {
			printErrors(errors);
			throw new JcResultException(errors);
		}
	}
}