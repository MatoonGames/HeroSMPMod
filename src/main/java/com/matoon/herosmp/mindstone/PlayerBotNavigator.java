package com.matoon.herosmp.mindstone;

import net.minecraft.block.BlockLadder;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import java.util.*;

/**
 * Small server-only A* navigator for a controlled real player.  It searches at most 384 nearby
 * nodes, only replans once per second, and has no dependency on a client automation mod.
 */
final class PlayerBotNavigator {
    private static final int SEARCH_LIMIT=384, RANGE=20;
    private static final Comparator<Node> BY_SCORE=(a,b)->Double.compare(a.f,b.f);
    private List<BlockPos> path=Collections.emptyList(); private int index; private BlockPos goal; private long nextRepath;

    BlockPos next(EntityPlayerMP player, BlockPos requested, long tick) {
        BlockPos start=feet(player);
        if(goal==null||goal.distanceSq(requested)>4||tick>=nextRepath||index>=path.size()) {
            goal=closestWalkable(player.world,requested); path=find(player.world,start,goal); index=path.size()>1?1:0; nextRepath=tick+20;
        }
        while(index<path.size()&&distanceTo(player,path.get(index))<.42D)index++;
        return index<path.size()?path.get(index):requested;
    }
    private static BlockPos feet(EntityPlayerMP p){return new BlockPos(MathHelper.floor(p.posX),MathHelper.floor(p.getEntityBoundingBox().minY),MathHelper.floor(p.posZ));}
    private static double distanceTo(EntityPlayerMP p,BlockPos b){double x=p.posX-(b.getX()+.5),z=p.posZ-(b.getZ()+.5);return Math.sqrt(x*x+z*z);}
    private static BlockPos closestWalkable(World w,BlockPos pos){for(int dy=2;dy>=-3;dy--){BlockPos p=pos.up(dy);if(walkable(w,p))return p;}return pos;}
    private static List<BlockPos> find(World w,BlockPos start,BlockPos end) {
        if(!walkable(w,start))start=closestWalkable(w,start); if(!walkable(w,end))return Collections.emptyList();
        PriorityQueue<Node> open=new PriorityQueue<>(64,BY_SCORE); Map<BlockPos,Node> known=new HashMap<>(512); Set<BlockPos> closed=new HashSet<>(512);
        Node first=new Node(start,null,0,heuristic(start,end)); open.add(first);known.put(start,first);int searched=0;
        while(!open.isEmpty()&&searched++<SEARCH_LIMIT){Node n=open.poll();if(!closed.add(n.pos))continue;if(n.pos.distanceSq(end)<=1)return build(n);
            addDirection(w,n,1,0,start,end,open,known,closed);addDirection(w,n,-1,0,start,end,open,known,closed);addDirection(w,n,0,1,start,end,open,known,closed);addDirection(w,n,0,-1,start,end,open,known,closed);
            if(ladder(w,n.pos)){BlockPos up=n.pos.up(),down=n.pos.down();if(walkable(w,up))addCandidate(n,up,start,end,open,known,closed);if(walkable(w,down))addCandidate(n,down,start,end,open,known,closed);}
        } return Collections.emptyList();
    }
    private static void addDirection(World w,Node node,int dx,int dz,BlockPos start,BlockPos end,PriorityQueue<Node> open,Map<BlockPos,Node> known,Set<BlockPos> closed){for(int dy=1;dy>=-1;dy--){BlockPos candidate=node.pos.add(dx,dy,dz);if(walkable(w,candidate)){addCandidate(node,candidate,start,end,open,known,closed);return;}}}
    private static void addCandidate(Node node,BlockPos candidate,BlockPos start,BlockPos end,PriorityQueue<Node> open,Map<BlockPos,Node> known,Set<BlockPos> closed){if(closed.contains(candidate)||Math.abs(candidate.getX()-start.getX())>RANGE||Math.abs(candidate.getZ()-start.getZ())>RANGE)return;double g=node.g+(candidate.getY()==node.pos.getY()?1:1.4);Node old=known.get(candidate);if(old==null||g<old.g){Node next=new Node(candidate,node,g,g+heuristic(candidate,end));known.put(candidate,next);open.add(next);}}
    private static boolean walkable(World w,BlockPos p){if(!w.isBlockLoaded(p)||!clear(w,p)||!clear(w,p.up()))return false;return solid(w,p.down())||ladder(w,p);}
    private static boolean clear(World w,BlockPos p){return w.isAirBlock(p)||ladder(w,p);}
    private static boolean solid(World w,BlockPos p){return w.getBlockState(p).getMaterial().blocksMovement();}
    private static boolean ladder(World w,BlockPos p){return w.getBlockState(p).getBlock() instanceof BlockLadder;}
    private static double heuristic(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())*1.4+Math.abs(a.getZ()-b.getZ());}
    private static List<BlockPos> build(Node n){LinkedList<BlockPos> out=new LinkedList<>();for(;n!=null;n=n.parent)out.addFirst(n.pos);return out;}
    private static class Node {final BlockPos pos;final Node parent;final double g,f;Node(BlockPos p,Node parent,double g,double f){pos=p;this.parent=parent;this.g=g;this.f=f;}}
}
