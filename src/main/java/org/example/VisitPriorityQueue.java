package org.example;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Очередь с сортировкой элементов по полю depth. Общая блокировока необходима, т. к. при добавлении элемента происходит
 * одновременная модификация нескольких переменных: списка уже посещенных страниц (visitedTitles) и обоих указателей(head, tail)
 */
public class VisitPriorityQueue implements Queue<WikiNode>{

    private final ReentrantLock lock;
    private final Condition notEmptyCondition;
    volatile private QueueNode head, tail;
    private final Set<String> visitedTitles;

    public VisitPriorityQueue() {
        this.lock = new ReentrantLock();
        this.notEmptyCondition = lock.newCondition();
        this.head = new QueueNode(null);
        this.tail = head;
        this.visitedTitles = new HashSet<>();
    }

    @Override
    public void enq(WikiNode item) {
        lock.lock();
        try{
            String currentLink = item.getTitle().toLowerCase();
            if(visitedTitles.contains(currentLink)){
                return;
            }
            visitedTitles.add(currentLink);
            QueueNode newQueueNode = new QueueNode(item);

            // если очередь пуста
            if(tail.getNext() == null){
                tail.setNext(newQueueNode);
                tail = newQueueNode;
                return;
            }

            // если все элементы выше по цепочке поиска - вставляем вначало
            if(head.getNext().getValue().getDepth() > item.getDepth()){
                newQueueNode.setNext(head.getNext());
                head.setNext(newQueueNode);
                notEmptyCondition.signalAll();
                return;
            }
            // если все элементы на том же уровне цепочки поиска - вставляем в конец
            if(tail.getValue().getDepth().equals(newQueueNode.getValue().getDepth())){
                tail.setNext(newQueueNode);
                tail = newQueueNode;
                return;
            }

            // вставка элемента в позицию в соответсвии с значением depth - в приоритете элементы с меньшим depth
            QueueNode currentElement = head.getNext();
            while(currentElement.getNext() != null){
                QueueNode nextElement = currentElement.getNext();
                if(nextElement.getValue().getDepth() > currentElement.getValue().getDepth()) {
                    newQueueNode.setNext(nextElement);
                    currentElement.setNext(newQueueNode);
                    return;
                }
                currentElement = nextElement;
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public WikiNode deq() {
        lock.lock();
        try{
            while(head.getNext() == null){
                try {
                    notEmptyCondition.await();
                } catch(InterruptedException interruptedException){
                    //
                }
            }
            QueueNode resultNode = head.getNext();
            head = resultNode;

            return resultNode.getValue();
        } finally {
            lock.unlock();
        }
    }

    private static class QueueNode{
        private final WikiNode value;
        private volatile QueueNode next;

        public QueueNode(WikiNode value) {
            this.value = value;
            this.next = null;
        }

        public void setNext(QueueNode next) {
            this.next = next;
        }

        public WikiNode getValue() {
            return value;
        }

        public QueueNode getNext() {
            return next;
        }
    }
}
