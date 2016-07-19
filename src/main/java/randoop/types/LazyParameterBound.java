package randoop.types;

import java.lang.reflect.*;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a type bound using reflection types.
 * Note that these objects may have recursive structure.
 */
class LazyParameterBound extends ParameterBound {

  /** the type for this bound */
  private final Type boundType;

  /**
   * Creates a {@code LazyParameterBound} from the given rawtype and type parameters.
   *
   * @param boundType  the reflection type for this bound
   */
  LazyParameterBound(Type boundType) {
    this.boundType = boundType;
  }

  /**
   * {@inheritDoc}
   * @return true if argument is a {@code LazyParameterBound}, and the rawtype
   *         and parameters are identical, false otherwise
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LazyParameterBound)) {
      return false;
    }
    LazyParameterBound b = (LazyParameterBound) obj;
    return this.boundType.equals(b.boundType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(boundType);
  }

  /**
   * {@inheritDoc}
   * @return the name of the type bound
   */
  @Override
  public String toString() {
    return boundType.toString();
  }

  /**
   * {@inheritDoc}
   * This generic type bound is satisfied by a concrete type if the concrete type
   * formed by applying the substitution to this generic bound is satisfied by
   * the concrete type.
   */
  @Override
  public boolean isUpperBound(GeneralType argType, Substitution<ReferenceType> substitution) {
    ReferenceBound b = this.apply(substitution);
    return b.isUpperBound(argType, substitution);
  }

  @Override
  boolean isUpperBound(ParameterBound bound, Substitution<ReferenceType> substitution) {
    assert false : " not quite sure what to do with lazy type bound";
    return false;
  }

  @Override
  public boolean isLowerBound(GeneralType argType, Substitution<ReferenceType> substitution) {
    ReferenceBound b = this.apply(substitution);
    return b.isLowerBound(argType, substitution);
  }

  @Override
  public boolean isSubtypeOf(ParameterBound boundType) {
    assert false : "LazyParameterBound.isSubtypeOf not implemented";
    return false;
  }

  @Override
  boolean hasWildcard() {
    assert false : "wildcard argument check not implemented in lazy bound";
    return false;
  }

  @Override
  public ParameterBound applyCaptureConversion() {
    assert false : "unable to do capture conversion on lazy bound";
    return this;
  }

  @Override
  public List<TypeVariable> getTypeParameters() {
    return getTypeParameters(boundType);
  }

  private List<TypeVariable> getTypeParameters(Type type) {
    List<TypeVariable> variableList = new ArrayList<>();
    if (type instanceof java.lang.reflect.TypeVariable) {
      variableList.add(TypeVariable.forType(type));
    } else if (type instanceof java.lang.reflect.ParameterizedType) {
      java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
      for (Type argType : pt.getActualTypeArguments()) {
        variableList.addAll(getTypeParameters(argType));
      }
    } else if (type instanceof java.lang.reflect.WildcardType) {
      java.lang.reflect.WildcardType wt = (java.lang.reflect.WildcardType) type;
      for (Type boundType : wt.getUpperBounds()) {
        variableList.addAll(getTypeParameters(boundType));
      }
      for (Type boundType : wt.getLowerBounds()) {
        variableList.addAll(getTypeParameters(boundType));
      }
    }
    return variableList;
  }

  @Override
  public ReferenceBound apply(Substitution<ReferenceType> substitution) {
    if (boundType instanceof java.lang.reflect.TypeVariable) {
      ReferenceType referenceType = substitution.get(boundType);
      if (referenceType != null) {
        return new ReferenceBound(referenceType);
      }
      throw new IllegalArgumentException(
          "substitution does not instantiate type variable: " + boundType);
    }

    if (boundType instanceof java.lang.reflect.ParameterizedType) {
      List<TypeArgument> argumentList = new ArrayList<>();
      for (Type parameter : ((ParameterizedType) boundType).getActualTypeArguments()) {
        TypeArgument typeArgument = apply(parameter, substitution);
        argumentList.add(typeArgument);
      }
      GenericClassType classType =
          GenericClassType.forClass((Class<?>) ((ParameterizedType) boundType).getRawType());
      InstantiatedType instantiatedType = new InstantiatedType(classType, argumentList);
      return new ReferenceBound(instantiatedType);
    }

    return null;
  }

  /**
   * Applies a substitution to a reflection type that occurs as an actual argument of a
   * parameterized type bound, to create a type argument to a {@link randoop.types.ParameterizedType}.
   *
   * @param type  the reflection type
   * @param substitution  the type substitution
   * @return the type argument
   */
  private TypeArgument apply(
      java.lang.reflect.Type type, Substitution<ReferenceType> substitution) {
    if (type instanceof java.lang.reflect.TypeVariable) {
      ReferenceType referenceType = substitution.get(type);
      if (referenceType != null) {
        return new ReferenceArgument(referenceType);
      }
      throw new IllegalArgumentException(
          "substitution does not instantiate type variable: " + boundType);
    }

    if (type instanceof java.lang.reflect.ParameterizedType) {
      List<TypeArgument> argumentList = new ArrayList<>();
      for (Type parameter : ((ParameterizedType) type).getActualTypeArguments()) {
        TypeArgument paramType = apply(parameter, substitution);
        argumentList.add(paramType);
      }
      GenericClassType classType =
          GenericClassType.forClass((Class<?>) ((ParameterizedType) type).getRawType());
      InstantiatedType instantiatedType = new InstantiatedType(classType, argumentList);
      return new ReferenceArgument(instantiatedType);
    }

    if (type instanceof Class) {
      return new ReferenceArgument(ClassOrInterfaceType.forType(type));
    }

    if (type instanceof java.lang.reflect.WildcardType) {
      //assert false : "not yet dealing with wildcards: " + type + " subst: " + substitution;
      final WildcardType wildcardType = (WildcardType) type;
      if (wildcardType.getLowerBounds().length > 0) {
        assert wildcardType.getLowerBounds().length == 1
            : "a wildcard is defined by the JLS to only have one bound";
        ParameterBound bound =
            ParameterBound.forTypes(wildcardType.getLowerBounds()).apply(substitution);

        return new WildcardArgumentWithLowerBound((ReferenceBound) bound);
      }
      // a wildcard always has an upper bound
      assert wildcardType.getUpperBounds().length == 1
          : "a wildcard is defined by the JLS to only have one bound";
      ParameterBound bound = ParameterBound.forTypes(wildcardType.getUpperBounds());
      bound = bound.apply(substitution);
      return new WildcardArgumentWithUpperBound((ReferenceBound) bound);
    }

    return null;
  }
}
