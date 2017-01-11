package it.unibz.inf.ontop.r2rml;

/*
 * #%L
 * ontop-obdalib-sesame
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
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

import eu.optique.api.mapping.R2RMLMappingManager;
import eu.optique.api.mapping.TriplesMap;
import eu.optique.api.mapping.impl.rdf4j.RDF4JR2RMLMappingManagerFactory;
import it.unibz.inf.ontop.injection.NativeQueryLanguageComponentFactory;
import it.unibz.inf.ontop.io.PrefixManager;
import it.unibz.inf.ontop.model.Function;
import it.unibz.inf.ontop.model.OBDAMappingAxiom;
import it.unibz.inf.ontop.model.OBDAModel;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import org.eclipse.rdf4j.model.Graph;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.GraphImpl;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.semanticweb.owlapi.model.OWLOntology;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.Injector;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class R2RMLWriter {

	private List<OBDAMappingAxiom> mappings;
	private URI sourceUri;
	private PrefixManager prefixmng;
	private OWLOntology ontology;
	private final NativeQueryLanguageComponentFactory nativeQLFactory;

    public R2RMLWriter(OBDAModel obdamodel, URI sourceURI, OWLOntology ontology, Injector injector)
	{
		this.sourceUri = sourceURI;
		this.mappings = obdamodel.getMappings(sourceUri);
		this.prefixmng = obdamodel.getPrefixManager();
		this.ontology = ontology;
		this.nativeQLFactory = injector.getInstance(NativeQueryLanguageComponentFactory.class);
	}


    /**
	 * call this method if you need the RDF Graph
	 * that represents the R2RML mappings
	 * @return an RDF Graph
	 */
	@Deprecated
	public Graph getGraph() {
		OBDAMappingTransformer transformer = new OBDAMappingTransformer();
		transformer.setOntology(ontology);
		List<Statement> statements = new ArrayList<Statement>();

		for (OBDAMappingAxiom axiom: this.mappings) {
			List<Statement> statements2 = transformer.getStatements(axiom,prefixmng);
			statements.addAll(statements2);
		}
		@SuppressWarnings("deprecation")
		Graph g = new GraphImpl();
		g.addAll(statements);
		return g;
	}

	public Collection <TriplesMap> getTripleMaps() {
		OBDAMappingTransformer transformer = new OBDAMappingTransformer();
		transformer.setOntology(ontology);
		return  splitMappingAxioms(this.mappings).stream()
				.map(a -> transformer.getTriplesMap(a, prefixmng))
				.collect(Collectors.toList());
	}

	private ImmutableSet<OBDAMappingAxiom> splitMappingAxioms(List<OBDAMappingAxiom> mappingAxioms) {
		/*
		 * Delimiter string d used for assigning ids to split mapping axioms.
		 * If a mapping axiom with id j is split into multiple ones,
		 * each of then will have "j"+"d"+int as an identifier
		 */
		String delimiterSubstring = getSplitMappingAxiomIdDelimiterSubstring(mappingAxioms);
		return mappingAxioms.stream()
				.flatMap(m -> splitMappingAxiom(m, delimiterSubstring).stream())
				.collect(ImmutableCollectors.toSet());
	}

	private String getSplitMappingAxiomIdDelimiterSubstring(List<OBDAMappingAxiom> mappingAxioms) {
		String delimiterSubstring = "";
		boolean matched;
		do {
			delimiterSubstring += "_";
			Pattern pattern = Pattern.compile(delimiterSubstring + "(\\d)*$");
			matched = mappingAxioms.stream()
					.anyMatch(a -> pattern.matcher(a.getId()).matches());
		} while(matched);
		return delimiterSubstring;
	}

	private ImmutableList<OBDAMappingAxiom> splitMappingAxiom(OBDAMappingAxiom mappingAxiom, String delimiterSubstring) {
		Multimap<Function, Function> subjectTermToTargetTriples = ArrayListMultimap.create();
		for(Function targetTriple : mappingAxiom.getTargetQuery()){
			Function subjectTerm = getFirstFunctionalTerm(targetTriple)
						.orElseThrow( () -> new IllegalStateException("Invalid OBDA mapping"));
			subjectTermToTargetTriples.put(subjectTerm, targetTriple);
		}
		// If the partition per target triple subject is non trivial
		if(subjectTermToTargetTriples.size() > 1){
			// Create ids for the new mapping axioms
			Map<Function, String> subjectTermToMappingIndex = new HashMap<>();
			int i = 1;
			for (Function subjectTerm : subjectTermToTargetTriples.keySet()){
				subjectTermToMappingIndex.put(subjectTerm, mappingAxiom.getId()+delimiterSubstring+i);
				i++;
			}
			// Generate one mapping axiom per subject
			return subjectTermToTargetTriples.asMap().entrySet().stream()
					.map(e -> nativeQLFactory.create(
							subjectTermToMappingIndex.get(e.getKey()),
							mappingAxiom.getSourceQuery(),
							new ArrayList<Function>(e.getValue())))
					.collect(ImmutableCollectors.toList());
		}
		return ImmutableList.of(mappingAxiom);
	}

	private Optional<Function> getFirstFunctionalTerm (Function inputFunction) {
		return inputFunction.getTerms().stream()
				.findFirst()
				.filter(t -> t instanceof Function)
				.map(t -> (Function) t);
	}


	/**
	 * the method to write the R2RML mappings
	 * from an rdf Model to a file
	 * @param file the ttl file to write to
	 */
	public void write(File file) throws Exception {
		try {
            FileOutputStream fos = new FileOutputStream(file);
			write(fos);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

    /**
     * the method to write the R2RML mappings
     * from an rdf Model to a file
     * @param os the output target
     */
    public void write(OutputStream os) throws Exception {
        try {
            R2RMLMappingManager mm = new RDF4JR2RMLMappingManagerFactory().getR2RMLMappingManager();
            Collection<TriplesMap> coll = getTripleMaps();
            Model out = mm.exportMappings(coll, Model.class);
            Rio.write(out, os, RDFFormat.TURTLE);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
			throw e;
		}
	}
}
