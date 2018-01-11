import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class fptminer {

	public static Map<Integer, List<Integer>> transList = new HashMap<Integer, List<Integer>>();
	public static int minsup;
	public static double minconf;
	public static List<List<Integer>> ruleConsequents;
	public static List<FrequentItemSet> frequentItemSet = new ArrayList<FrequentItemSet>();
	public static Map<Integer, Integer> itemCount = new HashMap<Integer, Integer>();//for storing item support count

	public static void main(String[] args) throws IOException {
		final long startTimeforFrequentItemSet = System.currentTimeMillis();
		BufferedReader br = null;
		int transactionId, item;
		String outputFile= null;
			minsup = Integer.parseInt(args[0]);
			minconf = Double.parseDouble(args[1]);
			String inputFile = args[2];
			outputFile = args[3];
			br = new BufferedReader(new FileReader(inputFile));
			String text = null;
			while((text=br.readLine())!=null) {
				String[] numbers = text.split(" ");
				transactionId = Integer.parseInt(numbers[0]);
				item = Integer.parseInt(numbers[1]);
				countItem(itemCount, item);
				List<Integer> itemList = null;
				if(!transList.containsKey(transactionId)) {
					itemList = new ArrayList<Integer>();
				}
				else {
					itemList = transList.get(transactionId);
				}
				itemList.add(item);
				transList.put(transactionId, itemList);
			}
		br.close();
		//remove items with frequency < minsup
		List<Integer> lowfrequencyItems = new ArrayList<Integer>();
		for(int i : itemCount.keySet()) {
			if(itemCount.get(i)< minsup) {
				lowfrequencyItems.add(i);
			}
		}
		for(int i : lowfrequencyItems)
		    itemCount.remove(i);
        
		//Build the header table 
		List<Node> header =  new ArrayList<Node>();
		
		for(int i: itemCount.keySet()) {
			Node n = new Node(i);
			n.setCount(itemCount.get(i));
			header.add(n);
		}
		sortNodes(header, itemCount);
		//sort the items in each transaction id according to their support
		for(List<Integer> items : transList.values()) {
			items.removeAll(lowfrequencyItems);
			sortItems(items, itemCount);
		}
		
		//build a fp tree
		
		buildFpTree(header);
		
		//apply the fp growth structure to get frequent item sets
		
		generateFrequentItems(header, new ArrayList<Integer>());
		for(Node n : header){
			List<Integer> i = new ArrayList<Integer>();
			i.add(n.getValue());
			frequentItemSet.add(new FrequentItemSet(i, n.getCount()));
		}
		/*for(FrequentItemSet a : frequentItemSet){
			System.out.println(a.getItemset()+":"+ a.getSupport());
		}*/
		System.out.println("total number of itemset:"+ frequentItemSet.size());
		final long duration1 = System.currentTimeMillis() - startTimeforFrequentItemSet;
		System.out.println("Time taken to generate frequent itemSets: "+ duration1+"ms");
		List<Rule> ruleList = null;
		if(minsup>20){
			final long startTimeforRuleGeneration = System.currentTimeMillis();
			ruleList = ruleGeneration();
			
			System.out.println("total number of association rules:"+ ruleList.size());
			final long duration2 = System.currentTimeMillis() - startTimeforRuleGeneration;
			System.out.println("Time taken for Rule Generation: "+ duration2+"ms");
			
		}
		
		/*for(Rule r : ruleList) {
			System.out.println(r.getLHS()+" "+r.getRHS()+" "+r.getSupport()+" "+r.getConfidence());
		}*/
		
		FileWriter fw= null;
			fw = new FileWriter(new File(outputFile));
			if(minsup>20){
				for(Rule r : ruleList){
					fw.write(r.getLHS()+" | "+r.getRHS()+" | "+r.getSupport()+" | "+r.getConfidence());
					fw.write("\n");
				}
			}
			else {
				for(FrequentItemSet a : frequentItemSet){
					fw.write(a.getItemset()+" | {} | "+ a.getSupport()+" | -1");
					fw.write("\n");
				}
			}
			fw.close();
	}
	
	
	public static void generateFrequentItems(List<Node> header, List<Integer> frequentItemSetPattern) {
		
		if(!frequentItemSetPattern.isEmpty()) {
			for(Node n : header) {
				List<Integer> frequentPattern = new ArrayList<Integer>();
				for(int i : frequentItemSetPattern) {
					frequentPattern.add(i);
				}
				if(n.getValue()!=-1)
					frequentPattern.add(n.getValue());
				frequentItemSet.add(new FrequentItemSet(frequentPattern, n.getCount()));//minsupport of the pattern will come here
			}
			
		}
		
		
		
		for(Node n: header) {
			List<Integer> pattern = new ArrayList<Integer>();
			pattern.add(n.getValue());
			pattern.addAll(frequentItemSetPattern);
			List<List<Integer>> conditionalList = new ArrayList<List<Integer>>();
			Map<Integer, Integer> map = new HashMap<Integer, Integer>();
			generateConditionalList(n, conditionalList);
			generateHeaderTable(conditionalList, map);
			generateConditionalTrees(conditionalList, map, pattern);
		}
	}
	
	public static void generateConditionalList(Node n,List<List<Integer>> conditionalList) {
		n = n.getSameNode();
		int count = 0;
		Node p;
		//making the item transaction set from the conditional items
		while(n != null) {
			List<Integer> itemset = new ArrayList<Integer>();
			count = n.getCount();
				p = n.getPreviousNode();
				while(p.getValue() != -1) {
					itemset.add(p.getValue());
					p = p.getPreviousNode();
				}
				while(count-->0)
					conditionalList.add(itemset);
				
				n = n.getSameNode();
			}
	}
	
	public static void generateHeaderTable(List<List<Integer>> conditionalList, Map<Integer, Integer> map) {
		for(List<Integer> itemset: conditionalList){
			for(int item: itemset){
				countItem(map, item);
			}
		}
	}
	
	
	
	public static void generateConditionalTrees(List<List<Integer>> conditionalList, Map<Integer, Integer> map, List<Integer> pattern) {
		//remove items with frequency < minsup
				List<Integer> lowfrequencyItems = new ArrayList<Integer>();
				for(int i : map.keySet()) {
					if(map.get(i)< minsup) {
						lowfrequencyItems.add(i);
					}
				}
				for(int i : lowfrequencyItems)
				    map.remove(i);
		        
				//Build the header table 
				List<Node> header =  new ArrayList<Node>();
				
				for(int i: map.keySet()) {
					Node n = new Node(i);
					n.setCount(map.get(i));
					header.add(n);
				}
				sortNodes(header, map);
				//sort the items in each transaction id according to their support
				for(List<Integer> items : conditionalList) {
					items.removeAll(lowfrequencyItems);
					sortItems(items, map);
				}
				
				// build conditionalTree
				
				Node rootNode = new Node();
				Node currentNode = rootNode;
					for(List<Integer> items : conditionalList) {
						for (Integer i : items) {
							Node newNode = getChild(i, currentNode);
							if (newNode == null) {
								newNode = new Node(i);
								newNode.setPreviousNode(currentNode);
								newNode.setCount();
								List<Node> nn = currentNode.getNextNode();
								if(nn == null)
									nn = new ArrayList<Node>();
								nn.add(newNode);
								currentNode.setNextNode(nn);
								addSameNode(header, newNode);
							}
							else
								newNode.setCount();
							currentNode = newNode;
							
						}
						currentNode = rootNode;
					}
					
					if(rootNode.getNextNode().isEmpty())
						return;
					generateFrequentItems(header, pattern);
	}
	
    public static List<Rule> ruleGeneration() {
    	List<Rule> ruleList = new ArrayList<Rule>();
    	List<List<Integer>> ruleGenItemSet = new ArrayList<List<Integer>>();
    	int maxsize = 0, size, support;
    	double confidence;
    	//sortList(frequentItemSet.keySet());
		for(FrequentItemSet fis: frequentItemSet){
			List<Integer> itemset = fis.getItemset();
			if(itemset.size()>1){
				ruleGenItemSet.add(itemset);
				if(maxsize<itemset.size())
					maxsize = itemset.size();
			}
		}
		
		for(List<Integer> itemset: ruleGenItemSet){
			sortItems(itemset, itemCount);
		}
		for(List<Integer> frequentItem : ruleGenItemSet){
			
			support = findSupport(frequentItem);
			size = frequentItem.size();
			ruleConsequents = combine(frequentItem);
			for(int i = 1; i<size; i++) {
				//call function that generates items of size i
				List<List<Integer>> ruleItems = generateRuleConsequent(ruleConsequents, i);
				//calculate the confidence and store the rules and support and confidence
				for(List<Integer> rulecon: ruleItems){
					List<Integer> ruleAntecedent = new ArrayList<Integer>(frequentItem);
					ruleAntecedent.removeAll(rulecon);
					int supportAntc = findSupport(ruleAntecedent);
					if(supportAntc > 0){
						confidence = (float)support/supportAntc;
						if(confidence >= minconf) {
							Rule r = new Rule(ruleAntecedent,rulecon, support, confidence);
							ruleList.add(r);
						}
						else {
							removeRuleConsequent(rulecon);
						}
					}
				}
			}
		}
		return ruleList;
			
	}
    
    public static int findSupport(List<Integer> ruleAntecedent){
    	int support = 0;
    	for(FrequentItemSet fis : frequentItemSet){
    		List<Integer> itemset = fis.getItemset();
    		if(ruleAntecedent.size() == itemset.size() && contains(itemset, ruleAntecedent)){
    			support = fis.getSupport();
    			break;
    		}
    	}
    	return support;
    }
    
    public static List<List<Integer>> generateRuleConsequent(List<List<Integer>> ruleConsequents, int size) {
    	List<List<Integer>> ruleItems = new ArrayList<List<Integer>>();
    	for(List<Integer> items : ruleConsequents){
    		if(items.size() == size)
    			ruleItems.add(items);
    	}
    	
    	return ruleItems;
    }
    
    public static List<List<Integer>> combine(List<Integer> frequentItem) {
    	ruleConsequents = new ArrayList<List<Integer>>();
		for(int i = 0; i < frequentItem.size(); i++)
		{
			List<Integer> items = null;
			int length = ruleConsequents.size();
			for(int j = 0; j<length; j++)
			{
				items = new ArrayList<Integer>();
				items.add(frequentItem.get(i));
				items.addAll(ruleConsequents.get(j));
				ruleConsequents.add(items);
				
			}
			items = new ArrayList<Integer>();
			items.add(frequentItem.get(i));
			ruleConsequents.add(items);
		}
		
		return ruleConsequents;
		
}
    
    public static void removeRuleConsequent(List<Integer> ruleCon) {
    	List<List<Integer>> lowFrequencyRuleCon = new ArrayList<List<Integer>>();
    	for(List<Integer> rules : ruleConsequents){
    		if(ruleCon.size()<rules.size() && contains(ruleCon, rules))
    			lowFrequencyRuleCon.add(rules);
    	}
    	ruleConsequents.removeAll(lowFrequencyRuleCon);
    }
    
    public static boolean contains(List<Integer> ruleCon, List<Integer> rules){
    	boolean isPresent = true;
    	for(int i: ruleCon) {
    		if(!rules.contains(i))
    			isPresent = false;
    	}
    	return isPresent;
    }
	
	public static void buildFpTree(List<Node> header) {
		Node rootNode = new Node();
		Node currentNode = rootNode;
		for (List<Integer> items : transList.values()) {
			for (Integer i : items) {
				Node newNode = getChild(i, currentNode);
				if (newNode == null) {
					newNode = new Node(i);
					newNode.setPreviousNode(currentNode);
					newNode.setCount();
					List<Node> nn = currentNode.getNextNode();
					if (nn == null)
						nn = new ArrayList<Node>();
					nn.add(newNode);
					currentNode.setNextNode(nn);
					addSameNode(header, newNode);
				} else
					newNode.setCount();
				currentNode = newNode;

			}
			currentNode = rootNode;
		}
	}
	
	public static Node addSameNode(List<Node> header, Node newNode) {
		Node head = null;
		for(Node n : header) {
			if(n.getValue() == newNode.getValue()) {
				head = n;
				while(n.getSameNode()!=null) {
					n = n.getSameNode();
				}
					n.setSameNode(newNode);
				break;
			}
		}
		return head;
	}
	
	public static Node getChild(Integer item, Node currentNode) {
		if(currentNode.getNextNode().isEmpty())
			return null;
		for(Node node : currentNode.getNextNode()) {
			if(node.getValue()==item)
				return node;
		}
		return null;
	}
	
	public static void sortItems(List<Integer> items, final Map<Integer, Integer> itemCount ) {
		
		Collections.sort(items, new Comparator<Integer>() {
			public int compare(Integer i1, Integer i2) {
				return (-1)*(itemCount.get(i1) - itemCount.get(i2));
			};
		});
		
	}
	
public static void sortNodes(List<Node> items, final Map<Integer, Integer> itemCount ) {
		
		Collections.sort(items, new Comparator<Node>() {
			public int compare(Node i1, Node i2) {
				return (-1)*(itemCount.get(i1.getValue()) - itemCount.get(i2.getValue()));
			};
		});
		
	}
	
		
	public static void countItem(Map<Integer, Integer> itemCount, int item) {
		int count;
		if(!itemCount.containsKey(item))
			count = 1;
		else {
			count = itemCount.get(item)+1;
		}
		itemCount.put(item, count);
	}
	
public static void sortList(List<List<Integer>> items) {
		
		Collections.sort(items, new Comparator<List<Integer>>() {
			public int compare(List<Integer> i1, List<Integer> i2) {
				return (-1)*(i1.size()) - (i2.size());
			};
		});
		
	}
	
}
     class FrequentItemSet {
	
	List<Integer> itemset;
	int support;
	FrequentItemSet(List<Integer> itemset, int support) {
		this.itemset = itemset;
		this.support = support;
	}
	public List<Integer> getItemset() {
		return itemset;
	}
	public void setItemset(List<Integer> itemset) {
		this.itemset = itemset;
	}
	public int getSupport() {
		return support;
	}
	public void setSupport(int support) {
		this.support = support;
	}

}
     
     class Rule {
 		
 		private List<Integer> LHS;
 		private List<Integer> RHS;
 		private int support;
 		private double confidence;
 		
 		Rule(List<Integer> LHS, List<Integer> RHS, int support, double confidence) {
 			this.LHS = LHS;
 			this.RHS = RHS;
 			this.support = support;
 			this.confidence = confidence;
 		}
 		public List<Integer> getLHS() {
 			return LHS;
 		}
 		public void setLHS(List<Integer> lHS) {
 			LHS = lHS;
 		}
 		public List<Integer> getRHS() {
 			return RHS;
 		}
 		public void setRHS(List<Integer> rHS) {
 			RHS = rHS;
 		}
 		public int getSupport() {
 			return support;
 		}
 		public void setSupport(int support) {
 			this.support = support;
 		}
 		public double getConfidence() {
 			return confidence;
 		}
 		public void setConfidence(double confidence) {
 			this.confidence = confidence;
 		}
 	}
     
    class Node {
    		private int value;
    		private int count;
    		private List<Node> nextNode;
    		private Node sameNode;
    		private Node previousNode;
    		
    		public Node() {
    			this.value = -1;
    		}
    		
    		public Node(int value) {
    			this.value = value;
    		}
    		public int getValue() {
    			return value;
    		}
    		public void setValue(int value) {
    			this.value = value;
    		}
    		public int getCount() {
    			return count;
    		}
    		public void setCount() {
    			this.count = this.count + 1;
    		}
    		
    		public void setCount(int count) {
    			this.count = count;
    		}
    		
    		public List<Node> getNextNode() {
    			if(nextNode == null)
    				return new ArrayList<Node>();
    			return nextNode;
    		}

    		public void setNextNode(List<Node> nextNode) {
    			this.nextNode = nextNode;
    		}

    		public Node getSameNode() {
    			return sameNode;
    		}
    		public void setSameNode(Node sameNode) {
    			this.sameNode = sameNode;
    		}
    		public Node getPreviousNode() {
    			return previousNode;
    		}

    		public void setPreviousNode(Node previousNode) {
    			this.previousNode = previousNode;
    		}
    	}

