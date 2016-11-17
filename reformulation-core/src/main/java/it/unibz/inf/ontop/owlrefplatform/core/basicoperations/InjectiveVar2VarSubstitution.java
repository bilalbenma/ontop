package it.unibz.inf.ontop.owlrefplatform.core.basicoperations;

import it.unibz.inf.ontop.model.ImmutableSubstitution;
import it.unibz.inf.ontop.model.ImmutableTerm;
import it.unibz.inf.ontop.model.Var2VarSubstitution;
import it.unibz.inf.ontop.model.Variable;

import java.util.Optional;
import java.util.Set;

/**
 * Var2VarSubstitution that is injective
 *    (no value in the substitution map is shared by two keys)
 */
public interface InjectiveVar2VarSubstitution extends Var2VarSubstitution {

    /**
     * Applies it (the Var2VarSubstitution) on the keys and values of the given substitution.
     */
    ImmutableSubstitution<ImmutableTerm> applyRenaming(ImmutableSubstitution<? extends ImmutableTerm> substitutionToRename);

    /**
     * { (x,y) | (x,y) \in (this o otherSubstitution), x not excluded }
     *
     *
     * Returns Optional.empty() when the resulting substitution is not injective (anymore).
     *
     * Variables to exclude from the domain are typically fresh temporary variables that can be ignored.
     * Ignoring them is sufficient in many cases to guarantee that the substitution is injective.
     *
     */
    Optional<InjectiveVar2VarSubstitution> composeWithAndPreserveInjectivity(InjectiveVar2VarSubstitution otherSubstitution,
                                                                             Set<Variable> variablesToExcludeFromTheDomain);


}