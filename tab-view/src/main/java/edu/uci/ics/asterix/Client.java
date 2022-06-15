/*
Author:
    Yunfan Qiao

With the help of:
    Wail Alkowaileet,
    Ian Maxon,
    Prof. Mike Carey
*/

package edu.uci.ics.asterix;

import edu.uci.ics.asterix.api.HttpAPIClient;
import edu.uci.ics.asterix.result.HashObject;
import edu.uci.ics.asterix.result.ResultObject;
import edu.uci.ics.asterix.result.Tuple;
import edu.uci.ics.asterix.result.metadata.*;

import java.io.IOException;
import java.util.*;

public class Client {
    private static final String DATATYPE_QUERY = "SELECT VALUE x FROM Metadata.`Datatype` x;";
    private static final String DATASET_QUERY = "SELECT VALUE x FROM Metadata.`Dataset` x;";
    private static final String INDEX_QUERY = "SELECT VALUE x FROM Metadata.`Index` x;";
    private static final String FUNCTION_QUERY = "SELECT VALUE x FROM Metadata.`Function` x;";
    private static final String FORMAT = "DataverseName (-s DatasetName?)? (-v newDataverseName)? (-i (DatasetName IndexName)?)? (-f (FunctionName, #args)?)?";

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args.length > 11) {
            System.out.println("Invalid number of arguments!");
            return;
        }

        if (args[0].equals("-h")) {
            System.out.println(FORMAT);
            return;
        }

        HttpAPIClient WailClient = new HttpAPIClient("localhost", "19002");
        ResultObject<Datatype> DatatypeObject = WailClient.getDatatypes(DATATYPE_QUERY);
        ResultObject<Dataset> DatasetObject = WailClient.getDatasets(DATASET_QUERY);
        ResultObject<Index> IndexObject = WailClient.getIndexes(INDEX_QUERY);
        ResultObject<Function> FunctionObject = WailClient.getFunctions(FUNCTION_QUERY);

        String DataverseName = args[0];

        //Some initialization... Plz help I wish to write an init function in ResultObject.java
        for (Datatype datatype : DatatypeObject.getResults())
            if (DataverseName.equals(datatype.getDataverseName())) {
                Tuple t = new Tuple(DataverseName, datatype.getDatatypeName());
                // Create a map with DatatypeName as key (we don't need to care about DatasetName because it won't change)
                HashObject.setResult(t, datatype);

                // We don't want to print the same Datatype twice, so we want to know if this Datatype is already found and printed.
                HashObject.setFound(t, false);
            }
        for (Dataset dataset : DatasetObject.getResults())
            if (DataverseName.equals(dataset.getDataverseName())) {
                Tuple t = new Tuple(DataverseName, dataset.getDatasetName());
                HashObject.setSettotype(t, dataset.getDatatypeName());
            }

        String DatasetName = null;
        String newDataverseName = null;
        String IndexName = null;
        String IndexDatasetName = null;
        String FunctionName = null;
        String argc = null;
        boolean sflag = false, vflag = false, iflag = false, fflag = false;

        try {
            for (int i = 1; i < args.length; i++)
                if (args[i].charAt(0) == '-') {
                    if (args[i].equals("-s")) {
                        if (sflag) throw new Exception();
                        sflag = true;
                        if ((i < args.length - 1) && (args[i + 1].charAt(0) != '-'))
                            DatasetName = args[++i];

                    } else if (args[i].equals("-v")) {
                        newDataverseName = args[++i];
                        if (vflag || newDataverseName.charAt(0) == '-')
                            throw new Exception();
                        vflag = true;

                    } else if (args[i].equals("-i")) {
                        if ((i < args.length - 1) && (args[i + 1].charAt(0) != '-')) {
                            IndexDatasetName = args[++i];
                            IndexName = args[++i];
                        }
                        if (iflag || IndexDatasetName != null && IndexName.charAt(0) == '-')
                            throw new Exception();
                        iflag = true;

                    } else if (args[i].equals("-f")) {
                        if ((i < args.length - 1) && (args[i + 1].charAt(0) != '-')) {
                            FunctionName = args[++i];
                            argc = args[++i];
                        }
                        if (fflag || argc != null && argc.charAt(0) == '-' && !argc.equals("-1"))
                            throw new Exception();
                        fflag = true;

                    } else {
                        System.out.println("Command not found!");
                        return;
                    }

                    // the commands must start with flags
                } else throw new Exception();
        } catch (Exception e) {
            System.out.println("Invalid arguments!");
            return;
        }


        //Check if Dataverse exists
        boolean bo = false;
        for (Dataset dataset : DatasetObject.getResults())
            if (DataverseName.equals(dataset.getDataverseName())) {
                bo = true;
                break;
            }
        if (!bo) {
            System.out.println("Dataverse not found!");
            return;
        }

        if (vflag) {
            // CREATE DATAVERSE
            printDataverse(newDataverseName);
        }

        if (sflag) {
            // CREATE DATATYPE
            // Note that the Indexes and Functions may need some of the Datatypes and Datasets.
            if (printDatatype(DataverseName, DatasetName, DatatypeObject) == -1) {
                System.out.println("Dataset not found!");
                return;
            }

            // CREATE DATASET
            printDataset(DatasetObject, DataverseName, DatasetName);
        }

        if (iflag) {
            // CREATE INDEX
            if (printIndex(DataverseName, IndexObject, IndexDatasetName, IndexName, DatatypeObject, DatasetObject) == -1)
                return;
        }

        if (fflag) {
            // CREATE FUNCTION
            if (printFunction(DataverseName, FunctionObject, FunctionName, argc, DatatypeObject, DatasetObject) == -1)
                return;
        }

    }

    private static boolean isFlattenedType(String s) {
        return s.equals("binary") || s.equals("boolean") || s.equals("circle") || s.equals("date") || s.equals("datetime") || s.equals("day-time-duration") ||
                s.equals("double") || s.equals("duration") || s.equals("float") || s.equals("geometry") || s.equals("int16") || s.equals("int32") ||
                s.equals("int64") || s.equals("int8") || s.equals("interval") || s.equals("line") || s.equals("missing") || s.equals("null") ||
                s.equals("point") || s.equals("point3d") || s.equals("polygon") || s.equals("rectangle") || s.equals("shortwithouttypeinfo") || s.equals("string") ||
                s.equals("time") || s.equals("uuid") || s.equals("year-month-duration");
    }


    // Note that if a type includes types from other Dataverses, it will auto create those types under the current Dataverse.
    // Therefore, the DataverseName will always be the same.
    private static void find_and_print(String DataverseName, String DatatypeName, Boolean first) {

        // Case 1: derived is RECORD
        Tuple myTuple = new Tuple(DataverseName, DatatypeName);
        Derived derived = HashObject.getResult(myTuple).getDerived();
        if (!derived.getTag().equals("RECORD")) {
            String s;

            // tag is either ORDEREDLIST or UNORDEREDLIST
            if (derived.getTag().equals("ORDEREDLIST"))
                s = derived.getOrderedList();
            else
                s = derived.getUnorderedList();

            System.out.print("[");
            // Note s may be flattened ([int])
            if (isFlattenedType(s))
                System.out.print(s);
            else {
                Tuple t = new Tuple(DataverseName, s);

                // If it's not anonymous, it should have been printed
                if (!HashObject.getResult(t).getDerived().getIsAnonymous())
                    System.out.print(s);
                else // If it's anonymous, then we will print everything
                    find_and_print(DataverseName, s, false);
            }

            System.out.print("]");
            return;
        }

        // Case 2: It's a regular derived. The record contains fields.
        if (!first) // if it's not the type we are recreating, it must be anonymous, and a curly bracket is needed.
            System.out.print("{");

        List<Fields> fields = derived.getRecord().getFields();

        boolean first_comma = true; // the last field does not need comma

        for (Fields field : fields) {
            if (first_comma)
                first_comma = false;
            else {
                System.out.print(", ");
                if (first) // just to make the table prettier.
                    System.out.print("\n");
            }

            String FieldName = field.getFieldName();
            String FieldType = field.getFieldType();

            if (first)
                System.out.print("\t");
            System.out.print(FieldName + ": ");
            if (isFlattenedType(FieldType))
                System.out.print(FieldType);
            else {
                Tuple t = new Tuple(DataverseName, FieldType);

                if (!HashObject.getResult(t).getDerived().getIsAnonymous())
                    System.out.print(FieldType);
                else // If it's anonymous, then we will print everything
                    find_and_print(DataverseName, FieldType, false);
            }
            if (field.isMissable()) System.out.print("?");
        }

        if (!first)
            System.out.print("}");
    }


    private static List<Tuple> topo_sort(Map<Tuple, List<Tuple>> tupleGraph, Map<Tuple, Integer> InDegree) {
        List<Tuple> result = new ArrayList<>();
        for (Map.Entry<Tuple, Integer> i : InDegree.entrySet())
            if (i.getValue() == 0) result.add(i.getKey());

        int head = 0, tail = result.size();
        while (head != tail) {
            Tuple u = result.get(head++);
            if (tupleGraph.containsKey(u))
                for (Tuple i : tupleGraph.get(u)) {
                    InDegree.put(i, InDegree.get(i) - 1);
                    if (InDegree.get(i) == 0) {
                        result.add(i);
                        tail++;
                    }
                }
        }
        //System.out.println(result.size());
        return result;
    }

    private static void printDataverse(String newDataverseName) {
        System.out.println("DROP DATAVERSE " + newDataverseName + " IF EXISTS;");
        System.out.println("CREATE DATAVERSE " + newDataverseName + ";");
        System.out.println("USE " + newDataverseName + ";\n");
    }

    private static int printDatatype(String DataverseName, String DatasetName, ResultObject<Datatype> DatatypeObject) {
        List<Tuple> tupleList = new ArrayList<>(); // This mimics the queue; we don't really want to pop any values.
        int head = 0, tail = 0;
        Map<Tuple, Integer> InDegree = new HashMap<>(); // This is for the topo-sort.

        // Case 1: we just want to print Datatypes associated with one Dataset
        if (DatasetName != null) {
            //Get DatatypeName here
            String DatatypeName = HashObject.getSettotype(new Tuple(DataverseName, DatasetName));

            // If Dataset/Datatype cannot be found
            if (DatatypeName == null) {
                return -1;
            }

            // Whenever we find a non-anonymous type that has not been printed, we put it at the end of the queue
            Tuple t = new Tuple(DataverseName, DatatypeName);
            // If it has not been printed when this function is called by printIndexes and printFunctions
            if (!HashObject.getPrinted(t)) {
                //System.out.printf("??? %s %s\n", DataverseName, DatasetName);
                HashObject.setPrinted(t);
                HashObject.setFound(t, true);
                tupleList.add(t);
                tail++;
                InDegree.put(t, 0);
            }

        } else
            // Case 2: we put every type in the queue
            for (Datatype datatype : DatatypeObject.getResults())
                // We only want non-anonymous Datatypes
                if (datatype.getDataverseName().equals(DataverseName) && !datatype.getDerived().getIsAnonymous()) {
                    Tuple t = new Tuple(DataverseName, datatype.getDatatypeName());
                    if (!HashObject.getPrinted(t)) {
                        HashObject.setPrinted(t);
                        HashObject.setFound(t, true);
                        tupleList.add(t);
                        tail++;
                        InDegree.put(t, 0);
                    }
                }

        if (tail == 0) return -1;

        Map<Tuple, List<Tuple>> tupleGraph = new HashMap<>();

        // Find all of the Datatypes (anonymous types included); for annotations check the "find_and_print" function.
        while (head != tail) {
            Tuple u = tupleList.get(head++);
            Derived derived = HashObject.getResult(u).getDerived();
            if (!derived.getTag().equals("RECORD")) {
                String s;
                if (derived.getTag().equals("ORDEREDLIST"))
                    s = derived.getOrderedList();
                else
                    s = derived.getUnorderedList();
                if (isFlattenedType(s)) continue;
                Tuple t = new Tuple(DataverseName, s);
                if (!HashObject.getPrinted(t)) {
                    HashObject.setPrinted(t);
                    tail += add_edge(u, t, tupleGraph, tupleList, InDegree, 1);
                }
            } else {
                List<Fields> fields = derived.getRecord().getFields();
                for (Fields field : fields) {
                    String s = field.getFieldType();
                    if (isFlattenedType(s)) continue;
                    Tuple t = new Tuple(DataverseName, s);
                    if (!HashObject.getPrinted(t)) {
                        HashObject.setPrinted(t);
                        tail += add_edge(u, t, tupleGraph, tupleList, InDegree, 1);
                    }
                }
            }
        }

        List<Tuple> topoList = topo_sort(tupleGraph, InDegree);
        for (Tuple i : topoList) {
            if (HashObject.getResult(i).getDerived().getIsAnonymous()) // Note that the topoList we got contains anonymous types
                continue;
            System.out.print("CREATE TYPE " + i.gety() + " AS {\n");
            find_and_print(i.getx(), i.gety(), true);
            System.out.print("\n};\n\n");
        }

        return 0;
    }

    private static void printDataset(ResultObject<Dataset> DatasetObject, String DataverseName, String DatasetName) {
        for (Dataset dataset : DatasetObject.getResults())
            if (dataset.getDataverseName().equals(DataverseName) && (DatasetName == null || dataset.getDatasetName().equals(DatasetName))) {
                // Iff it's a view, internalDetails is null
                if (dataset.getInternalDetails() == null) continue;

                System.out.println("CREATE DATASET " + dataset.getDatasetName() + "(" + dataset.getDatatypeName() + ")");
                System.out.print("\tPRIMARY KEY ");
                boolean comma = false;
                for (List<String> i : dataset.getInternalDetails().getPrimaryKey()) {
                    if (comma) System.out.print(", ");
                    else comma = true;
                    boolean period = false;
                    for (String j : i) {
                        if (period) System.out.print(".");
                        else period = true;
                        System.out.print(j);
                    }
                }

                // It's autogenerated;
                if (dataset.getInternalDetails().getAutogenerated())
                    System.out.print(" AUTOGENERATED");

                System.out.println(";\n");
            }
    }


    // if t is visited return 1; it's for the tail (man I wish to use & to pass by reference)
    // Will time be wasted for passing lists and maps for arguments?
    private static int add_edge(Tuple u, Tuple t, Map<Tuple, List<Tuple>> tupleGraph, List<Tuple> tupleList, Map<Tuple, Integer> InDegree, int found_num) {
        int newTuple = 0;
        if (found_num == 1) {
            if (!HashObject.getFound(t)) {
                HashObject.setFound(t, true);
                tupleList.add(t);
                newTuple = 1;
            }
        } else {
            if (!HashObject.getFound2(t)) {
                HashObject.setFound2(t, true);
                tupleList.add(t);
                newTuple = 1;
            }
        }

        InDegree.putIfAbsent(t, 0);
        tupleGraph.putIfAbsent(t, new ArrayList<>());
        List<Tuple> lis = tupleGraph.get(t);
        lis.add(u);
        tupleGraph.put(t, lis); // add the edge t -> u
        InDegree.putIfAbsent(u, 0);
        InDegree.put(u, InDegree.get(u) + 1); // increase the in-degree of t
        return newTuple;
    }

    private static int printIndex(String DataverseName, ResultObject<Index> IndexObject, String DatasetName, String IndexName, ResultObject<Datatype> DatatypeObject, ResultObject<Dataset> DatasetObject) {
        if (IndexName != null) {
            // If the Dataset has not been printed, the output will not be runnable!
            Tuple t = new Tuple(DataverseName, DatasetName);
            if (DatasetName != null) {
                if (printDatatype(DataverseName, DatasetName, DatatypeObject) != -1)
                    printDataset(DatasetObject, DataverseName, DatasetName);
            }

            String DatatypeName = HashObject.getSettotype(new Tuple(DataverseName, DatasetName));
            boolean bo = false;
            for (Index i : IndexObject.getResults())
                if (i.getDataverseName().equals(DataverseName) && i.getDatasetName().equals(DatasetName) && i.getIndexName().equals(IndexName)) {
                    bo = true;
                    printIndexHelper(i, DataverseName, DatatypeName);
                    break;
                }
            if (!bo) {
                System.out.print("Index not found!");
                return -1;
            }
        } else {
            for (Index i : IndexObject.getResults())
                if (i.getDataverseName().equals(DataverseName) && !i.getIsPrimary()) {
                    Tuple t = new Tuple(DataverseName, i.getDatasetName());
                    if (!HashObject.getPrinted(t)) {
                        if (printDatatype(DataverseName, i.getDatasetName(), DatatypeObject) != -1)
                            printDataset(DatasetObject, DataverseName, i.getDatasetName());
                    }

                    String DatatypeName = HashObject.getSettotype(new Tuple(DataverseName, i.getDatasetName()));
                    printIndexHelper(i, DataverseName, DatatypeName);
                }
        }

        return 0;
    }

    private static void printIndexHelper(Index index, String DataverseName, String DatatypeName) {
        // If it's true, it means that it is auto-created when creating the dataset
        if (index.getIsPrimary()) return;

        System.out.print("CREATE INDEX " + index.getIndexName() + " ON " + index.getDatasetName() + "(");

        // so you can't do "fieldname: datatype" if fieldname is already defined...
        boolean comma = false;
        Iterator<String> it = null;
        if (index.getSearchKeyType() != null)
            it = index.getSearchKeyType().iterator();

        for (List<String> i : index.getSearchKey()) {
            if (comma) System.out.print(", ");
            else comma = true;

            boolean period = false;
            String current = null;
            for (String j : i) {
                if (period) {
                    System.out.print(".");
                    if (index.getSearchKeyType() != null && current != null)
                        current = find_next(DataverseName, current, j);
                } else {
                    period = true;
                    if (index.getSearchKeyType() != null)
                        current = find_next(DataverseName, DatatypeName, j);
                }
                System.out.print(j);
            }

            if (index.getSearchKeyType() != null) {
                String Datatype = it.next();
                if (current == null)
                    System.out.print(": " + Datatype);
            }

        }

        System.out.print(")" + " TYPE ");

        if (index.getIndexStructure().equals("BTREE"))
            System.out.print("BTREE");
        if (index.getIndexStructure().equals("RTREE"))
            System.out.print("RTREE");
        if (index.getIndexStructure().equals("LENGTH_PARTITIONED_WORD_INVIX"))
            System.out.print("KEYWORD");
        if (index.getIndexStructure().equals("SINGLE_PARTITION_WORD_INVIX")) // This is NOT a typo!!!
            System.out.print("FULLTEXT");
        if (index.getIndexStructure().equals("LENGTH_PARTITIONED_NGRAM_INVIX"))
            System.out.print("NGRAM(" + index.getGramLength() + ")");

        if (index.getIsEnforced() != null)
            System.out.print(" ENFORCED");

        System.out.println(";\n");
    }

    private static String find_next(String DataverseName, String DatatypeName, String FieldName) {
        Tuple myTuple = new Tuple(DataverseName, DatatypeName);
        if (HashObject.getResult(myTuple) == null) return null;
        if (FieldName == null) return DatatypeName;
        Derived derived = HashObject.getResult(myTuple).getDerived();

        // Since lists cannot be indexed, derived must contains fields.
        List<Fields> fields = derived.getRecord().getFields();
        for (Fields field : fields)
            if (field.getFieldName().equals(FieldName))
                return field.getFieldType();

        return null;
    }

    // This will be similar to printDatatype
    private static int printFunction(String DataverseName, ResultObject<Function> FunctionObject, String FunctionName, String argc, ResultObject<Datatype> DatatypeObject, ResultObject<Dataset> DatasetObject) {
        // Initialize functionHash.
        for (Function i : FunctionObject.getResults()) {
            if (!i.getDataverseName().equals(DataverseName))
                continue;
            Tuple t = new Tuple(i.getName(), i.getArity());
            HashObject.setFunctionHash(t, i);
        }

        List<Tuple> tupleList = new ArrayList<>();
        int head = 0, tail = 0;
        Map<Tuple, Integer> InDegree = new HashMap<>();

        // Case 1: we just want to print one Function and its dependencies.
        if (FunctionName != null) {
            Tuple t = new Tuple(FunctionName, argc);

            // If Dataset/Datatype cannot be found
            if (!HashObject.getFunctionHash().containsKey(t)) {
                System.out.println("Function not found!");
                return -1;
            }

            // Whenever we find a function that has not been printed, we put it at the end of the queue
            HashObject.setFound2(t, true);
            tupleList.add(t);
            tail++;
            InDegree.put(t, 0);
        } else
            // Case 2: we put every Function in the queue
            for (Function i : FunctionObject.getResults())
                if (i.getDataverseName().equals(DataverseName)) {
                    Tuple t = new Tuple(i.getName(), i.getArity());
                    HashObject.setFound2(t, true);
                    tupleList.add(t);
                    tail++;
                    InDegree.put(t, 0);
                }

        if (tail == 0) return 0;

        Map<Tuple, List<Tuple>> tupleGraph = new HashMap<>();

        // Find all of the Datatypes (anonymous types included); for annotations check the "find_and_print" function.
        while (head != tail) {
            Tuple u = tupleList.get(head++);
            Function func = HashObject.getFunctionHash(u);

            // Dependencies[0]: dependent Datasets
            for (List<String> i : func.getDependencies().get(0)) {
                if (printDatatype(i.get(0), i.get(1), DatatypeObject) != -1)
                    printDataset(DatasetObject, i.get(0), i.get(1));
            }

            // Dependencies[1]: dependent Functions
            for (List<String> i : func.getDependencies().get(1)) {
                if (!i.get(0).equals(DataverseName))
                    continue;
                tail += add_edge(u, new Tuple(i.get(1), i.get(2)), tupleGraph, tupleList, InDegree, 2);
            }
        }

        List<Tuple> topoList = topo_sort(tupleGraph, InDegree);
        for (Tuple i : topoList) {
            Function func = HashObject.getFunctionHash(i);
            System.out.print("CREATE OR REPLACE FUNCTION " + i.getx() + "(");
            if (func.getArity().equals("-1"))
                System.out.print("...");
            else {
                boolean first_comma = true;
                for (String j : func.getParams()) {
                    if (first_comma)
                        first_comma = false;
                    else
                        System.out.print(", ");
                    System.out.print(j);
                }
            }
            System.out.println(") {");
            System.out.println(func.getDefinition()); // Unfortunately I'm too lazy to do the indentations XD.
            System.out.println("};\n");
        }

        return 0;
    }
}


/*
DROP DATAVERSE YunfanData IF EXISTS;
CREATE DATAVERSE YunfanData;
USE YunfanData;

CREATE TYPE SubType AS {
	id: int,
    a: int
};

CREATE TYPE SmallType AS {
	id: int,
    a: SubType
};

CREATE TYPE BigType AS {
	id: int,
    a: SubType,
    b: SmallType,
    c: string
};

CREATE TYPE AutoType AS {
	id: uuid
};

CREATE DATASET SmallSet(SmallType)
	PRIMARY KEY id;

CREATE DATASET BigSet(BigType)
	PRIMARY KEY b.a.id;

CREATE DATASET AutoSet(AutoType)
	PRIMARY KEY id AUTOGENERATED;

CREATE INDEX SmallIndex ON SmallSet(a.id);

CREATE INDEX BigIndex ON BigSet(c) TYPE NGRAM(3);

CREATE FUNCTION SmallFunction (SmallSet) {
	abs(SmallSet.id)
};

CREATE FUNCTION RandomFunction (...) {
	abs(SmallFunction(args[0])) + abs(args[1].id)
};
*/