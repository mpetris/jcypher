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

package iot.jcypher.domain.genericmodel;

import iot.jcypher.domain.genericmodel.DOType.Builder;
import iot.jcypher.domain.genericmodel.DOType.Kind;
import iot.jcypher.domain.mapping.surrogate.AbstractSurrogate;
import iot.jcypher.graph.GrNode;
import iot.jcypher.graph.GrProperty;
import iot.jcypher.query.api.IClause;
import iot.jcypher.query.factories.clause.CREATE;
import iot.jcypher.query.factories.clause.RETURN;
import iot.jcypher.query.factories.clause.SEPARATE;
import iot.jcypher.query.values.JcNode;
import iot.jcypher.query.values.JcNumber;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;

public class DomainModel {

	private static final String JavaPkg = "java.";
	private static final String[] primitives = new String[] { "int", "boolean",
			"long", "float", "double" };
	private static final String EnumVals = "ENUM$VALUES";
	private static final String TypeNodePostfix = "_mdl";
	private static final String Colon = ":";
	private static final String propTypeName = "typeName";
	private static final String propSuperTypeName = "superTypeName";
	private static final String propInterfaceNames = "interfaceNames";
	private static final String propFields = "fields";
	private static final String propKind = "kind";

	private String domainName;
	private String typeNodeName;
	private Map<String, DOType> doTypes;
	private List<DOType> unsaved;
	private ClassPool classPool;

	DomainModel(String domName, String domLabel) {
		super();
		this.domainName = domName;
		this.typeNodeName = domLabel.concat(TypeNodePostfix);
		this.doTypes = new HashMap<String, DOType>();
		this.unsaved = new ArrayList<DOType>();
	}

	public DOType addType(Class<?> clazz) {
		if (!AbstractSurrogate.class.isAssignableFrom(clazz)) {
			String name = clazz.getName();
			DOType doType;
			if ((doType = this.doTypes.get(name)) == null) {
				doType = InternalAccess.createDOType(name);
				this.doTypes.put(name, doType);
				boolean buildIn = doType.isBuildIn();
				if (!buildIn) {
					Builder builder = InternalAccess.createBuilder(doType);
					Kind kind = clazz.isInterface() ? Kind.INTERFACE :
							Enum.class.isAssignableFrom(clazz) ? Kind.ENUM : Modifier.isAbstract(clazz
							.getModifiers()) ? Kind.ABSTRACT_CLASS : Kind.CLASS;
					InternalAccess.setKind(builder, kind);
					this.unsaved.add(doType);
					addFields(builder, clazz);
					Class<?> sClass = clazz.getSuperclass();
					DOType superType = null;
					if (sClass != null)
						superType = addType(sClass);
					if (superType != null)
						InternalAccess.setSuperType(builder, superType);
					Class<?>[] ifss = clazz.getInterfaces();
					if (ifss != null) {
						for (Class<?> ifs : ifss) {
							DOType interf = addType(ifs);
							if (interf != null)
								doType.getInterfaces().add(interf);
						}
					}
				}
			}
			return doType;
		}
		return null;
	}

	private void addFields(Builder builder, Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		for (int i = 0; i < fields.length; i++) {
			if (!Modifier.isTransient(fields[i].getModifiers())) {
				if (builder.build().getKind() == Kind.ENUM
						&& fields[i].getName().equals(EnumVals))
					continue;
				Class<?> fTyp = fields[i].getType();
				String tName = fTyp.getName();
				DOField fld = new DOField(fields[i].getName(), tName);
				builder.build().getFields().add(fld);
				if (!builder.build().isBuildIn())
					addType(fTyp);
			}
		}
	}

	public DOType getDOType(String typeName) {
		return this.doTypes.get(typeName);
	}

	public String getDomainName() {
		return domainName;
	}

	public String getTypeNodeName() {
		return this.typeNodeName;
	}

	public void loadFrom(List<GrNode> mdlInfos) {
		for (GrNode nd : mdlInfos) {
			if (nd != null) {
				GrProperty propTyp = nd.getProperty(propTypeName);
				GrProperty propSuperTyp = nd.getProperty(propSuperTypeName);
				GrProperty propFlds = nd.getProperty(propFields);
				GrProperty propKnd = nd.getProperty(propKind);
				GrProperty propIfss = nd.getProperty(propInterfaceNames);

				String typNm = propTyp.getValue().toString();

				DOType doType = addType(typNm);
				doType.setNodeId(nd.getId());
				
				Kind knd = Kind.valueOf(propKnd.getValue().toString());
				
				Builder builder = InternalAccess.createBuilder(doType);
				InternalAccess.setKind(builder, knd);

				String sTypNm = propSuperTyp.getValue().toString();
				if (!sTypNm.isEmpty())
					InternalAccess.setSuperType(builder, addType(sTypNm));

				Object flds = propFlds.getValue();
				if (flds instanceof List<?>) {
					for (Object obj : (List<?>) flds) {
						String[] fld = obj.toString().split(":");
						DOField doField = new DOField(fld[0], fld[1]);
						doType.getFields().add(doField);
					}
				}

				Object ifss = propIfss.getValue();
				if (ifss instanceof List<?>) {
					for (Object obj : (List<?>) ifss) {
						doType.getInterfaces().add(addType(obj.toString()));
					}
				}
			}
		}
	}

	private DOType addType(String typeName) {
		DOType typ = this.doTypes.get(typeName);
		if (typ == null) {
			typ = InternalAccess.createDOType(typeName);
			this.doTypes.put(typeName, typ);
		}
		return typ;
	}

	public boolean hasChanged() {
		return this.unsaved.size() > 0;
	}

	@SuppressWarnings("unchecked")
	public List<IClause>[] getChangeClauses() {
		List<IClause> clauses = null;
		List<IClause> returnClauses = null;
		if (hasChanged()) {
			clauses = new ArrayList<IClause>();
			returnClauses = new ArrayList<IClause>();
			int idx = 0;
			for (DOType t : this.unsaved) {
				List<String> flds = new ArrayList<String>();
				for (DOField f : t.getFields()) {
					String fd = f.getName().concat(Colon)
							.concat(f.getTypeName());
					flds.add(fd);
				}
				List<String> ifss = new ArrayList<String>();
				for (DOType ifs : t.getInterfaces()) {
					String ifName = ifs.getName();
					ifss.add(ifName);
				}
				String sTypeName = t.getSuperType() != null ? t.getSuperType()
						.getName() : "";
				String strIdx = String.valueOf(idx);
				JcNode n = new JcNode("n_".concat(strIdx));
				JcNumber nid = new JcNumber("nid_".concat(strIdx));
				IClause clause = CREATE.node(n).label(getTypeNodeName())
						.property(propTypeName).value(t.getName())
						.property(propKind).value(t.getKind())
						.property(propSuperTypeName).value(sTypeName)
						.property(propInterfaceNames).value(ifss)
						.property(propFields).value(flds);
				clauses.add(SEPARATE.nextClause());
				clauses.add(clause);
				returnClauses.add(RETURN.value(n.id()).AS(nid));
				idx++;
			}
			return new List[] { clauses, returnClauses };
		}
		return null;
	}

	public static boolean isBuildIn(String typeName) {
		return typeName.startsWith(JavaPkg) || isPrimitive(typeName);
	}

	private static boolean isPrimitive(String typeName) {
		for (String prim : primitives) {
			if (prim.equals(typeName))
				return true;
		}
		return false;
	}

	public List<DOType> getUnsaved() {
		return unsaved;
	}

	public void updatedToGraph() {
		this.unsaved.clear();
	}

	public Class<?> getClassForName(String name) throws ClassNotFoundException {
		Class<?> clazz;
		try {
			clazz = Class.forName(name);
		} catch (ClassNotFoundException e) {
			DOType doType = getDOType(name);
			if (doType == null)
				throw e;
			try {
				createClassFor(doType);
				clazz = Class.forName(name);
			} catch (Throwable e1) {
				if (e1 instanceof ClassNotFoundException)
					throw (ClassNotFoundException)e1;
				else
					throw new RuntimeException(e1);
			}
		}
		return clazz;
	}
	
	public DomainObject createDomainObjectFor(Object obj) {
		String typNm = obj.getClass().getName();
		DOType typ = getDOType(typNm);
		if (typ == null)
			throw new RuntimeException("missing type: [".concat(typNm).concat("] in domain model"));
		DomainObject dobj = new DomainObject(typ);
		InternalAccess.setRawObject(dobj, obj);
		return dobj;
	}

	private void createClassFor(DOType doType) throws Throwable {
		createCtClassFor(doType, getClassPool());
	}

	private CtClass createCtClassFor(DOType doType, ClassPool cp)
			throws Throwable {
		CtClass cc = cp.getOrNull(doType.getName());
		if (cc == null) {
			if (doType.getKind() == Kind.INTERFACE) {
				cc = cp.makeInterface(doType.getName());
			} else {
				cc = cp.makeClass(doType.getName());
				if (doType.getKind() == Kind.ABSTRACT_CLASS)
					cc.setModifiers(cc.getModifiers() | Modifier.ABSTRACT);
//				if (doType.getKind() == Kind.ENUM) // enum modifier (see java.lang.Class)
//					cc.setModifiers(cc.getModifiers() | javassist.Modifier.ENUM);
			}
			DOType doSType = doType.getSuperType();
			if (doSType != null) {
				CtClass scc = createCtClassFor(doSType, cp);
				cc.setSuperclass(scc);
			}
			for (DOType ifs : doType.getInterfaces()) {
				CtClass ifct = createCtClassFor(ifs, cp);
				cc.addInterface(ifct);
			}

			if (doType.getKind() == Kind.ENUM) {
				int count = 0;
				for (DOField fld : doType.getFields()) {
					if (fld.getTypeName().equals(doType.getName()))
						count++;
				}
				// enum values field
				StringBuilder sb = new StringBuilder();
				sb.append("private static ");
				sb.append(doType.getName());
				sb.append("[] values = new ");
				sb.append(doType.getName());
				sb.append('[');
				sb.append(count);
				sb.append("];");
				CtField ctField = CtField.make(sb.toString(), cc);
				cc.addField(ctField);
				// enum values method
				sb = new StringBuilder();
				sb.append("public static ");
				sb.append(doType.getName());
				sb.append("[] ");
				sb.append("values(){return values;}");
				CtMethod mthd = CtMethod.make(sb.toString(), cc);
				cc.addMethod(mthd);
				// enum constructor
				sb = new StringBuilder();
				int idx = doType.getName().lastIndexOf('.');
				String nm = idx >= 0 ? doType.getName().substring(idx + 1)
						: doType.getName();
				sb.append("public ");
				sb.append(nm);
				sb.append("(String name, int ordinal) {super(name, ordinal);}");
				CtConstructor constr = CtNewConstructor.make(sb.toString(), cc);
				cc.addConstructor(constr);
			} else {
				for (DOField fld : doType.getFields()) {
					CtField ctField;
					String tn = fld.getTypeName();
					if (!fld.isBuidInType()) {
						DOType ft = getDOType(tn);
						if (ft == null)
							throw new ClassNotFoundException(tn);
						CtClass ctFt = createCtClassFor(ft, cp);
						ctField = new CtField(ctFt, fld.getName(), cc);
						ctField.setModifiers(javassist.Modifier.PUBLIC);
					} else {
						StringBuilder sb = new StringBuilder();
						sb.append("public ");
						sb.append(tn);
						sb.append(' ');
						sb.append(fld.getName());
						sb.append(';');
						ctField = CtField.make(sb.toString(), cc);
					}
					cc.addField(ctField);
				}
			}
			cc.toClass(); // creates the class and registers it with the class
							// loader
			if (doType.getKind() == Kind.ENUM) { // add the enum values
													// dynamically
				Class<?> clazz = Class.forName(doType.getName());
				Field f = clazz.getDeclaredField("values");
				f.setAccessible(true);
				Object vals = f.get(clazz);
				Constructor<?> cstr = clazz.getDeclaredConstructor(
						String.class, int.class);
//				ConstructorAccessor constr = ReflectionFactory.getReflectionFactory().newConstructorAccessor(cstr);
				int ord = 0;
				for (DOField fld : doType.getFields()) {
					if (fld.getTypeName().equals(doType.getName())) {
//						Object val = constr.newInstance(new Object[]{fld.getName(), ord});
						Object val = cstr.newInstance(fld.getName(), ord);
						((Object[])vals)[ord] = val;
						ord++;
					}
				}
//				Object[] enums=clazz.getEnumConstants();
//				clazz = clazz;
			}
		}

		return cc;
	}

	private ClassPool getClassPool() {
		if (this.classPool == null)
			this.classPool = new ClassPool(true);
		return this.classPool;
	}

	public String asString() {
		String indent = "   ";
		StringBuilder sb = new StringBuilder();
		sb.append(this.domainName);
		sb.append(" {");
		List<DOType> vals = new ArrayList<DOType>();
		vals.addAll(this.doTypes.values());
		Collections.sort(vals, new Comparator<DOType>() {
			@Override
			public int compare(DOType o1, DOType o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		for (DOType t : vals) {
			sb.append('\n');
			sb.append(t.asString(indent));
		}
		sb.append('\n');
		sb.append('}');
		return sb.toString();
	}
}
