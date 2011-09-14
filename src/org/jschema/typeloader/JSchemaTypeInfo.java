package org.jschema.typeloader;

import gw.internal.gosu.parser.AnnotationInfo;
import gw.lang.Autoinsert;
import gw.lang.annotation.Annotations;
import gw.lang.function.Function0;
import gw.lang.parser.ISymbol;
import gw.lang.reflect.*;
import gw.lang.reflect.IRelativeTypeInfo.Accessibility;
import gw.lang.reflect.java.IJavaType;
import gw.util.concurrent.LazyVar;
import org.jschema.model.JsonList;
import org.jschema.model.JsonMap;
import org.jschema.model.JsonObject;
import org.jschema.rpc.SimpleRPCCallHandler;
import org.jschema.util.JSchemaUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class JSchemaTypeInfo extends TypeInfoBase {

  private IJSchemaType owner;
  private Map json;
  private Map<String, String> jsonSlotToPropertyName = new HashMap<String, String>();
  private Map<String, String> propertyNameToJsonSlot = new HashMap<String, String>();
  private List<IPropertyInfo> properties;

  private LazyVar<List<IMethodInfo>> methods = new LazyVar<List<IMethodInfo>>() {
    @Override
    protected List<IMethodInfo> init() {
      return buildMethods();
    }
  };
  private IMethodInfo _convertToMethod;
  private IMethodInfo _findMethod;

  private List<IMethodInfo> buildMethods() {
    if (isJsonEnum()) {
      return (List) TypeSystem.get(JSchemaEnumType.JsonEnumValue.class).getTypeInfo().getMethods();
    } else {
      List<IMethodInfo> typeMethods = new ArrayList<IMethodInfo>();
      typeMethods.add(new MethodInfoBuilder()
        .withName("write")
        .withReturnType(IJavaType.STRING)
        .withStatic(true)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return JSchemaUtils.serializeJSON(ctx);
          }
        })
        .build(JSchemaTypeInfo.this));
      typeMethods.add(new MethodInfoBuilder()
        .withName("prettyPrint")
        .withParameters(new ParameterInfoBuilder()
          .withType(IJavaType.INTEGER)
          .withName("indent")
          .withDefValue(ISymbol.NULL_DEFAULT_VALUE))
        .withReturnType(IJavaType.STRING)
        .withStatic(true)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            Integer indent = Integer.valueOf(2);
            if (args[0] != null) {
              indent = (Integer) args[0];
            }
            return JSchemaUtils.serializeJSON(ctx, indent);
          }
        })
        .build(JSchemaTypeInfo.this));
      typeMethods.add(parseMethod()
        .withParameters(new ParameterInfoBuilder()
          .withType(IJavaType.STRING)
          .withName("content"))
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return JSchemaUtils.parseJSON((String) args[0], getOwnersType());
          }
        })
        .build(JSchemaTypeInfo.this));
      typeMethods.add(parseMethod()
        .withParameters(new ParameterInfoBuilder()
          .withType(TypeSystem.get(java.net.URL.class))
          .withName("content"))
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            try {
              java.net.URL content = (java.net.URL) args[0];
              BufferedReader reader = new BufferedReader(new InputStreamReader(content.openConnection().getInputStream()));
              StringBuilder builder = new StringBuilder();
              String line = reader.readLine();
              while (line != null) {
                builder.append(line);
                line = reader.readLine();
              }
              return JSchemaUtils.parseJSON(builder.toString(), getOwnersType());
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        })
        .build(JSchemaTypeInfo.this));
      typeMethods.add(new MethodInfoBuilder()
        .withName("get")
        .withParameters(new ParameterInfoBuilder()
          .withType(IJavaType.STRING)
          .withName("url"),
          new ParameterInfoBuilder()
            .withType(IJavaType.MAP.getParameterizedType(IJavaType.STRING, IJavaType.OBJECT))
            .withDefValue(ISymbol.NULL_DEFAULT_VALUE)
            .withName("args")
        )
        .withReturnType(getOwnersType())
        .withStatic(true)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            Map<String, String> fixedArgs = fixArgs((Map) args[1]);
            return JSchemaUtils.parseJSONValue(SimpleRPCCallHandler.doGet((String) args[0], fixedArgs), getOwnersType());
          }
        })
        .build(JSchemaTypeInfo.this));

      typeMethods.add(new MethodInfoBuilder()
        .withName("post")
        .withParameters(new ParameterInfoBuilder()
          .withType(IJavaType.STRING)
          .withName("url"),
          new ParameterInfoBuilder()
            .withType(IJavaType.MAP.getParameterizedType(IJavaType.STRING, IJavaType.OBJECT))
            .withDefValue(ISymbol.NULL_DEFAULT_VALUE)
            .withName("args")
        )
        .withReturnType(getOwnersType())
        .withStatic(true)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            Map<String, String> fixedArgs = fixArgs((Map) args[1]);
            return JSchemaUtils.parseJSONValue(SimpleRPCCallHandler.doPost((String) args[0], fixedArgs), getOwnersType());
          }
        })
        .build(JSchemaTypeInfo.this));

      typeMethods.add(new MethodInfoBuilder()
        .withName("asJson")
        .withReturnType(TypeSystem.get(JsonMap.class))
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return ctx;
          }
        })
        .build(JSchemaTypeInfo.this));

      ITypeVariableType typeVar = TypeSystem.getOrCreateTypeVariableType("T", IJavaType.OBJECT, getOwnersType());
      IType typeVarType = TypeSystem.getTypeFromObject(typeVar);

      _convertToMethod = new MethodInfoBuilder()
        .withName("convertTo")
        .withTypeVars(typeVar.getTypeVarDef().getTypeVar())
        .withParameters(new ParameterInfoBuilder()
          .withName("type")
          .withType(typeVarType))
        .withReturnType(typeVar)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            JsonMap ctxMap = (JsonMap) ctx;
            IType targetType = (IType) args[0];
            if (!(targetType instanceof IJSchemaType)) {
              throw new IllegalArgumentException("Can only be converted to other JSchema types!");
            }
            return JSchemaUtils.cloneToType((IJSchemaType) targetType, ctxMap, getOwnersType());
          }
        })
        .build(JSchemaTypeInfo.this);
      typeMethods.add(_convertToMethod);

      IType outerParent = TypeSystem.getByFullNameIfValid(getOwnersType().getNamespace());
      final IType parentType;
      if (outerParent instanceof IJSchemaType && !thisIsTypedefFor((IJSchemaType) outerParent)) {
        parentType = outerParent;
      } else {
        parentType = TypeSystem.get(JsonMap.class);
      }

      typeMethods.add(new MethodInfoBuilder()
        .withName("parent")
        .withReturnType(parentType)
        .withCallHandler(new IMethodCallHandler() {

          @Override
          public Object handleCall(Object ctx, Object... args) {
            JsonMap jsonMap = (JsonMap) ctx;
            JsonObject parent = jsonMap.getParent();
            while (parent != null && !isStronglyTypedMap(parent)) {
              parent = parent.getParent();
            }
            return parent;
          }
        }).build(this));

      typeMethods.add(new MethodInfoBuilder()
        .withName("descendents")
        .withReturnType(Iterable.class)
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            JsonMap jsonMap = (JsonMap) ctx;
            return jsonMap.getDescendents();
          }
        }).build(this));

      ITypeVariableType typeVar2 = TypeSystem.getOrCreateTypeVariableType("T", IJavaType.OBJECT, getOwnersType());
      IType typeVarType2 = TypeSystem.getTypeFromObject(typeVar2);
      _findMethod = new MethodInfoBuilder()
        .withName("find")
        .withTypeVars(typeVar2.getTypeVarDef().getTypeVar())
        .withParameters(new ParameterInfoBuilder()
          .withName("type")
          .withType(typeVarType2))
        .withReturnType(IJavaType.LIST.getParameterizedType(typeVar2))
        .withCallHandler(new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            JsonMap ctxMap = (JsonMap) ctx;
            return ctxMap.findDescendents((IType) args[0]);
          }
        })
        .build(JSchemaTypeInfo.this);
      typeMethods.add(_findMethod);

      return typeMethods;
    }
  }

  private MethodInfoBuilder parseMethod() {
    return new MethodInfoBuilder()
      .withName("parse")
      .withReturnType(getOwnersType())
      .withStatic(true);
  }

  private static Map<String, String> fixArgs(Map arg) {
    if (arg == null) {
      return Collections.emptyMap();
    }
    else {
      HashMap<String, String> fixedArgs = new HashMap(arg.size());
      for (Object key : arg.keySet()) {
        Object value = arg.get(key);
        if (!(key instanceof String)) {
          key = "" + key;
        }
        if (!(value instanceof String)) {
          value = "" + value;
        }
        fixedArgs.put((String) key, (String) value);
      }
      return fixedArgs;
    }
  }

  public JSchemaTypeInfo(JSchemaType owner, Object object) {
    this.owner = owner;
    this.json = (Map)object;
    createProperties();
  }

	private void createProperties() {
    if (isJsonEnum()) {
      properties = createEnumProperties();
    } else {
      properties = createStructProperties();
    }
  }

  private boolean isJsonEnum() {
    return getOwnersType() instanceof IEnumType;
  }

  private List<IPropertyInfo> createEnumProperties() {
    ArrayList<IPropertyInfo> props = new ArrayList<IPropertyInfo>();
    IEnumType type = (IEnumType) getOwnersType();
    for (final IEnumValue enumValue : type.getEnumValues()) {
      PropertyInfoBuilder property = new PropertyInfoBuilder()
        .withName(enumValue.getCode())
        .withStatic()
        .withType(type)
        .withWritable(false)
        .withAccessor(new IPropertyAccessor() {
          @Override
          public Object getValue(Object ctx) {
            return enumValue;
          }

          @Override
          public void setValue(Object ctx, Object value) {
            throw new UnsupportedOperationException("Cannot set an enum");
          }
        });
      props.add(property.build(this));
    }
    return props;
  }

  private List<IPropertyInfo> createStructProperties() {
    ArrayList<IPropertyInfo> props = new ArrayList<IPropertyInfo>();
    HashSet<String> propNames = new HashSet<String>();

    for (Object k : json.keySet()) {

      final String jsonSlotName = (String) k;
      String propertyName = JSchemaUtils.convertJSONStringToGosuIdentifier(jsonSlotName);
      final Object value = json.get(jsonSlotName);

      jsonSlotToPropertyName.put(jsonSlotName, propertyName);
      propertyNameToJsonSlot.put(propertyName, jsonSlotName);

      final IType propType = getOwnersType().resolveInnerType(getOwnersType() + "."+ propertyName, value);

      PropertyInfoBuilder pib = new PropertyInfoBuilder()
        .withName(propertyName)
        .withType(propType);
      propNames.add(propertyName);

      pib.withAccessor(new IPropertyAccessor() {
        @Override
        public void setValue(Object ctx, Object value) {
          JsonMap json = (JsonMap) ctx;
          json.put(jsonSlotName, value);
        }

        @Override
        public Object getValue(Object ctx) {
          JsonMap json = (JsonMap) ctx;
          return json.get(jsonSlotName);
        }
      });

      if (propType instanceof IEnumType) {
        //ignore
      } else if (propType instanceof IJSchemaType) {
        pib.withAnnotations(makeMapAutoCreateAnnotation(propType));
      } else if (TypeSystem.get(JsonMap.class).equals(propType.getGenericType())) {
        pib.withAnnotations(makeMapAutoCreateAnnotation(propType));
      } else if (TypeSystem.get(JsonList.class).equals(propType.getGenericType())) {
        pib.withAnnotations(makeListAutoCreateAnnotation(propType),
          makeListAutoInsertAnnotation());
      }

      props.add(pib.build(this));
    }

    return props;
  }

  private IAnnotationInfo makeMapAutoCreateAnnotation(final IType propType) {
    return makeAutocreateAnnotation(new Function0() {
      @Override
      public Object invoke() {
        return new JsonMap(propType);
      }
    });
  }

  private IAnnotationInfo makeListAutoCreateAnnotation(final IType propType) {
    return makeAutocreateAnnotation(new Function0() {
      @Override
      public Object invoke() {
        return new JsonList(propType);
      }
    });
  }

  private IAnnotationInfo makeListAutoInsertAnnotation() {
    return new AnnotationInfo(TypeSystem.get(Autoinsert.class), Annotations.create(Autoinsert.class), this);
  }

  private IAnnotationInfo makeAutocreateAnnotation(Function0 function) {
    IType autocreateType = TypeSystem.getByFullName("gw.lang.Autocreate");
    List<? extends IConstructorInfo> constructors = autocreateType.getTypeInfo().getConstructors();
    for (IConstructorInfo constructor : constructors) {
      if (constructor.getParameters().length == 1) {
        return new AnnotationInfo(
          autocreateType,
          constructor.getConstructor().newInstance(function), this);
      }
    }
    throw new IllegalStateException("Could not find the block constructor for Autocreate");
  }

  private boolean isStronglyTypedMap(JsonObject parent) {
    if (parent instanceof JsonMap) {
      return parent.getIntrinsicType() instanceof IJSchemaType;
    } else {
      return false;
    }
  }

  private boolean thisIsTypedefFor(IJSchemaType outerParent) {
    return  outerParent.getTypeDefs().containsValue(getOwnersType().getName());
  }

  private IConstructorInfo defaultConstructor = new ConstructorInfoBuilder()
			.withConstructorHandler(new IConstructorHandler() {
				@Override
				public Object newInstance(Object... args) {
          return new JsonMap(getOwnersType());
        }
			}).withAccessibility(Accessibility.PUBLIC).build(this);

	@Override
	public List<? extends IConstructorInfo> getConstructors() {
		List<IConstructorInfo> constructors = new ArrayList<IConstructorInfo>();
		constructors.add(defaultConstructor);
		return constructors;
	}

	@Override
	public List<? extends IMethodInfo> getMethods() {
		return methods.get();
	}

	@Override
	public List<? extends IPropertyInfo> getProperties() {
		return properties;
	}

	@Override
	public IPropertyInfo getProperty(CharSequence propName) {
		for (IPropertyInfo prop : properties) {
			if (propName.equals(prop.getName())) {
				return prop;
			}
		}
		return null;
	}

	@Override
	public CharSequence getRealPropertyName(CharSequence propName) {
		for (IPropertyInfo prop : properties) {
			if (propName.equals(prop.getName())) {
				return prop.getName();
			}
		}
		return null;
	}

  @Override
  public IMethodInfo getMethod(CharSequence methodName, IType... params) {
    //Not sure why I need to do this, seems like the generics system should work this out
    if ("convertTo".equals(methodName) && params.length == 1 && params[0] instanceof IMetaType) {
      return _convertToMethod;
    } else if ("find".equals(methodName) && params.length == 1 && params[0] instanceof IMetaType) {
      return _findMethod;
    } else {
      return super.getMethod(methodName, params);
    }
  }

  @Override
	public List<IAnnotationInfo> getDeclaredAnnotations() {
		return Collections.emptyList();
	}

	@Override
	public boolean hasAnnotation(IType type) {
		return false;
	}

	@Override
	public IJSchemaType getOwnersType() {
		return owner;
	}

	public String toString() {
		return properties.toString();
	}

  public IType getTypeForJsonSlot(String key) {
    String propertyName = jsonSlotToPropertyName.get(key);
    if (propertyName != null) {
      IPropertyInfo property = getProperty(propertyName);
      if (property != null) {
        return property.getFeatureType();
      }
    }
    return null;
  }

  public String getJsonSlotForPropertyName(String propName) {
    return propertyNameToJsonSlot.get(propName);
  }
}
