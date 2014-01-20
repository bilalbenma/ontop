package it.unibz.krdb.obda.quest.sparql;

/*
 * #%L
 * ontop-test
 * %%
 * Copyright (C) 2009 - 2013 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import it.unibz.krdb.obda.io.ModelIOManager;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.owlrefplatform.core.QuestConstants;
import it.unibz.krdb.obda.owlrefplatform.core.QuestPreferences;
import it.unibz.krdb.obda.owlrefplatform.owlapi3.QuestOWL;
import it.unibz.krdb.obda.owlrefplatform.owlapi3.QuestOWLConnection;
import it.unibz.krdb.obda.owlrefplatform.owlapi3.QuestOWLFactory;
import it.unibz.krdb.obda.owlrefplatform.owlapi3.QuestOWLResultSet;
import it.unibz.krdb.obda.owlrefplatform.owlapi3.QuestOWLStatement;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;


import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Tests that the system can handle the SPARQL "regex" FILTER
 * This is a parameterized class, see junit documentation
 * Tests oracle, mysql, postgres and mssql
 */
@RunWith(org.junit.runners.Parameterized.class)
public class RegexpTest extends TestCase {

	// TODO We need to extend this test to import the contents of the mappings
	// into OWL and repeat everything taking form OWL

	private OBDADataFactory fac;
	private QuestOWLConnection conn;

	Logger log = LoggerFactory.getLogger(this.getClass());
	private OBDAModel obdaModel;
	private OWLOntology ontology;

	final String owlfile = "src/main/resources/testcases-scenarios/virtual-mode/stockexchange/simplecq/stockexchange.owl";
	private String obdafile;
	
	private QuestOWL reasoner;

	/**
	 * Constructor is necessary for parameterized test
	 */
	public RegexpTest(String database, boolean isH2){
		this.obdafile = "src/main/resources/testcases-scenarios/virtual-mode/stockexchange/simplecq/stockexchange-" + database + ".obda";
	}


	/**
	 * Returns the obda files for the different database engines
	 */
	@Parameters
	public static Collection<Object[]> getObdaFiles(){
		return Arrays.asList(new Object[][] {
				 {"mysql", false },
				 {"pgsql", false},
				 {"oracle", false }});
	}

	
	@Override
	@Before
	public void setUp() throws Exception {
		
		// Loading the OWL file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		ontology = manager.loadOntologyFromOntologyDocument((new File(owlfile)));

		// Loading the OBDA data
		fac = OBDADataFactoryImpl.getInstance();
		obdaModel = fac.getOBDAModel();
		
		ModelIOManager ioManager = new ModelIOManager(obdaModel);
		ioManager.load(obdafile);
	
		QuestPreferences p = new QuestPreferences();
		p.setCurrentValueOf(QuestPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
		p.setCurrentValueOf(QuestPreferences.OBTAIN_FULL_METADATA, QuestConstants.FALSE);
		// Creating a new instance of the reasoner
		QuestOWLFactory factory = new QuestOWLFactory();
		factory.setOBDAController(obdaModel);

		factory.setPreferenceHolder(p);

		reasoner = (QuestOWL) factory.createReasoner(ontology, new SimpleConfiguration());

		// Now we are ready for querying
		conn = reasoner.getConnection();

		
	}
	
	@After
	public void tearDown() throws Exception{
		conn.close();
		reasoner.dispose();
	}
	

	
	private String runTests(String query) throws Exception {
		QuestOWLStatement st = conn.createStatement();
		String retval;
		try {
			QuestOWLResultSet rs = st.executeTuple(query);
			assertTrue(rs.nextRow());
			OWLIndividual ind1 =	rs.getOWLIndividual("x")	 ;
			retval = ind1.toString();
		} catch (Exception e) {
			throw e;
		} finally {
			try {

			} catch (Exception e) {
				st.close();
			}
			conn.close();
			reasoner.dispose();
		}
		return retval;
	}

	/**
	 * Tests the use of SPARQL like
	 * @throws Exception
	 */
	@Test
	public void testSparqlRegexpFilter() throws Exception {
		String query = "PREFIX : <http://www.owl-ontologies.com/Ontology1207768242.owl#> SELECT DISTINCT ?x WHERE { ?x a :StockBroker. ?x :firstName ?name. FILTER regex (?name, 'J[ano]*').}";
		String broker = runTests(query);
		assertEquals(broker, "<http://www.owl-ontologies.com/Ontology1207768242.owl#person-112>");
	}
	
	/**
	 * Tests the use of case-insensitive SPARQL like
	 * @throws Exception
	 */
	@Test
	public void testSparqlCaseinsensitiveRegexFilter() throws Exception {
		String query = "PREFIX : <http://www.owl-ontologies.com/Ontology1207768242.owl#> SELECT DISTINCT ?x WHERE { ?x a :StockBroker. ?x :firstName ?name. FILTER regex (?name, 'j[ANO]*', 'i').}";
		String broker = runTests(query);
		assertEquals(broker, "<http://www.owl-ontologies.com/Ontology1207768242.owl#person-112>");
	}
		
}
