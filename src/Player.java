import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

class Player {

	public static List<Ship> myShips;
	public static List<Ship> otherShips;
	public static List<Barrel> barrels;
	public static List<Mine> mines;
	public static List<CannonBall> cannonBalls;
	public static int turn = 0;
	public static int[][] rumMap;
	public static boolean[][] mineMap;

	public static void main(String args[]) {
		@SuppressWarnings("resource")
		Scanner in = new Scanner(System.in);

		// game loop
		while (true) {
			turn++;
			in.nextInt(); // myShipCount
			int entityCount = in.nextInt();
			myShips = new ArrayList<>();
			otherShips = new ArrayList<>();
			barrels = new ArrayList<>();
			mines = new ArrayList<>();
			cannonBalls = new ArrayList<>();
			rumMap = new int[MAP_WIDTH][MAP_HEIGHT];
			mineMap = new boolean[MAP_WIDTH][MAP_HEIGHT];

			for (int i = 0; i < entityCount; i++) {
				int id = in.nextInt();
				String entityType = in.next();
				int x = in.nextInt();
				int y = in.nextInt();
				int arg1 = in.nextInt();
				int arg2 = in.nextInt();
				int arg3 = in.nextInt();
				int arg4 = in.nextInt();

				switch (entityType) {
				case "SHIP":
					Ship ship = new Ship(id, x, y, arg1, arg2, arg3, arg4);
					if (arg4 == 1) myShips.add(ship);
					else otherShips.add(ship);
					break;
				case "BARREL":
					Barrel barrel = new Barrel(id, x, y, arg1);
					rumMap[x][y] = arg1;
					barrels.add(barrel);
					break;
				case "MINE":
					Mine mine = new Mine(id, x, y);
					mineMap[x][y] = true;
					mines.add(mine);
					break;
				case "CANNONBALL":
					CannonBall ball = new CannonBall(id, x, y, arg1, arg2);
					cannonBalls.add(ball);
					break;
				}

			}
			for (Ship ship : myShips) {
				System.err.println("==== " + ship);
				System.err.println("Front " + frontPos(ship));
				System.err.println("Back " + backPos(ship));

				CannonBall targetedBy = isTargeted(ship, cannonBalls);
				if (targetedBy != null) System.err.println("is targeted by " + targetedBy);

				boolean againstWall = !ship.nextPos.isInsideMap();

				Barrel b = closestBarrel(ship, barrels);
				Ship enemy = closestEnemy(ship, otherShips);

				if (againstWall) {
					System.err.println("--> Against wall");
					String move = getMoveAgainstWall(ship, turn);
					System.out.println(move);
				} else if (ship.speed == 0 && targetedBy != null) {
					System.err.println("--> Speed = 0 & targeted");
					System.out.println("FASTER");
				} else if (ship.speed == 0) {
					System.err.println("--> FASTER");
					System.out.println("FASTER");
				} else if (ship.pos.distanceTo(enemy.pos) < 8) {
					Coord target = target(ship, enemy);
					System.err.println("--> Firing to enemy at " + enemy.pos + ", target is " + target);
					System.out.println("FIRE " + target);
				} else if (b == null) {
					System.err.println("--> No barrel left, moving to enemy " + enemy.pos);
					System.out.println("MOVE " + enemy.pos);
				} else {
					System.err.println("Moving to barrel " + b);
					Move m = moveToTarget(ship, b.pos);
					System.err.println("--> " + m.toString());
					System.out.println(m.toString());
				}
				System.err.println();

			}
		}
	}

	/************************************************************************
	 * A*
	 ************************************************************************/
	public static class PathNode {
		Coord pos;
		Move move;
		int dist;
		int orientation;
		int speed;
		PathNode prev;
		int score;

		public PathNode(Move move, Coord pos, int orientation, int speed, int dist, PathNode prev) {
			this.move = move;
			this.pos = pos;
			this.orientation = orientation;
			this.speed = speed;
			this.dist = dist;
			this.prev = prev;
		}

		public void score(Coord target) {
			this.score = dist + abs(target.x - pos.x) + abs(target.y + pos.y);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) return true;
			if (!(o instanceof PathNode)) { return false; }
			PathNode n = (PathNode) o;
			return n.pos.x == pos.x && n.pos.y == pos.y && n.orientation == orientation && n.speed == speed;
		}

		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + pos.x;
			result = 31 * result + pos.y;
			result = 31 * result + orientation;
			result = 31 * result + speed;
			return result;
		}

		@Override
		public String toString() {
			return "Node at " + pos + ", orientation = " + orientation + " score " + score;
		}
	}

	public static enum Move {
		PORT, STARBOARD, WAIT, FASTER, SLOWER
	}

	private static Move firstMove(PathNode node) {
		PathNode n = node;
		while (n.prev != null && n.prev.prev != null) {
			n = n.prev;
		}
		return n.move;
	}

	/**
	 * Return position of the front/ back of a ship/pos;
	 */
	public static Coord frontPos(Ship ship) {
		return frontPos(ship.pos, ship.orientation);
	}

	public static Coord backPos(Ship ship) {
		return backPos(ship.pos, ship.orientation);
	}

	public static Coord frontPos(Coord pos, int orientation) {
		int newY, newX;
		if (pos.y % 2 == 1) {
			newY = pos.y + Coord.DIRECTIONS_ODD[orientation][1];
			newX = pos.x + Coord.DIRECTIONS_ODD[orientation][0];
		} else {
			newY = pos.y + Coord.DIRECTIONS_EVEN[orientation][1];
			newX = pos.x + Coord.DIRECTIONS_EVEN[orientation][0];
		}
		return new Coord(newX, newY);
	}

	public static Coord backPos(Coord pos, int orientation) {
		int newY, newX;
		int o = (orientation + 3) % 6;
		if (pos.y % 2 == 1) {
			newY = pos.y + Coord.DIRECTIONS_ODD[o][1];
			newX = pos.x + Coord.DIRECTIONS_ODD[o][0];
		} else {
			newY = pos.y + Coord.DIRECTIONS_EVEN[o][1];
			newX = pos.x + Coord.DIRECTIONS_EVEN[o][0];
		}

		return new Coord(newX, newY);
	}

	/**
	 * Return best move to go to target.
	 */
	public static Move moveToTarget(Ship ship, Coord target) {
		PathNode init = new PathNode(Move.WAIT, ship.pos, ship.orientation, ship.speed, 0, null);
		init.score(target);

		Set<PathNode> closedSet = new HashSet<>();
		Set<PathNode> openSet = new HashSet<>();
		openSet.add(init);

		while (!openSet.isEmpty()) {
			PathNode cur = getBestPathNode(openSet);
			// System.err.println(" A* Cur = " + cur);
			if (cur.pos.x == target.x && cur.pos.y == target.y) { return firstMove(cur); }

			openSet.remove(cur);
			closedSet.add(cur);

			Coord front = frontPos(cur.pos, cur.orientation);
			Coord back = backPos(cur.pos, cur.orientation);
			if (front.isInsideMap() && mineMap[front.x][front.y]) continue;
			if (back.isInsideMap() && mineMap[back.x][back.y]) continue;

			Coord nextPos = cur.pos.neighbor(cur.orientation);
			if (!nextPos.isInsideMap()) continue;

			// PORT
			PathNode port = new PathNode(Move.PORT, nextPos, (cur.orientation + 1) % 6, cur.speed, cur.dist + 1, cur);
			PathNode starboard = new PathNode(Move.STARBOARD, nextPos, (cur.orientation + 5) % 6, cur.speed, cur.dist + 1, cur);
			PathNode nop = new PathNode(Move.WAIT, nextPos, cur.orientation, cur.speed, cur.dist + 1, cur);

			port.score(target);
			starboard.score(target);
			nop.score(target);

			if (!closedSet.contains(nop) && !openSet.contains(nop)) openSet.add(nop);
			if (!closedSet.contains(port) && !openSet.contains(port)) openSet.add(port);
			if (!closedSet.contains(starboard) && !openSet.contains(starboard)) openSet.add(starboard);
		}

		return Move.WAIT;

	}

	private static PathNode getBestPathNode(Set<PathNode> nodes) {
		PathNode res = null;
		int bestScore = Integer.MAX_VALUE;
		for (PathNode n : nodes) {
			if (n.score < bestScore) {
				bestScore = n.score;
				res = n;
			}
		}
		return res;
	}

	private static CannonBall isTargeted(Ship ship, List<CannonBall> balls) {
		for (CannonBall ball : balls) {
			if (ball.pos.distanceTo(ship.pos) < 2) return ball;
		}
		return null;
	}

	/**
	 * Calcule la bonne target pour tirer sur le ship other.
	 */
	private static Coord target(Ship me, Ship other) {
		if (other.speed == 0) return other.pos;

		// Compute pos of enemy in (1 + (distance to target) / 3) turns
		int dist = me.pos.distanceTo(other.pos);
		int turns = dist / 3 + 2;

		CubeCoordinate res = other.pos.toCubeCoordinate();
		for (int i = 0; i < turns; i++) {
			CubeCoordinate next = res.neighbor(other.orientation);
			if (!next.toOffsetCoordinate().isInsideMap()) break;
			res = next;
		}
		Coord c = res.toOffsetCoordinate();

		System.err.println("Position of target after " + turns + " turns is " + c);
		// Don't target yourself
		CubeCoordinate futureMe = me.pos.toCubeCoordinate();
		for (int i = 0; i < turns; i++) {
			futureMe = futureMe.neighbor(me.orientation);
			if (futureMe.distanceTo(res) < 2) return other.pos;
		}
		return c;
	}

	/**
	 * Return the closest enemy.
	 */
	private static Ship closestEnemy(Ship ship, List<Ship> ships) {
		int dist = Integer.MAX_VALUE;
		Ship res = null;
		for (Ship s : ships) {
			int d = ship.pos.distanceTo(s.pos);
			if (d < dist) {
				dist = d;
				res = s;
			}
		}
		return res;
	}

	/**
	 * Return closest barrel.
	 */
	private static Barrel closestBarrel(Ship ship, List<Barrel> barrels) {
		int dist = Integer.MAX_VALUE;
		Barrel res = null;
		for (Barrel barrel : barrels) {
			int d = ship.pos.distanceTo(barrel.pos);
			if (d < dist) {
				dist = d;
				res = barrel;
			}
		}
		return res;
	}

	/**
	 * Renvoie le bon mouvement lorsque le ship est contre un mur (next position
	 * outside map)
	 */
	private static String getMoveAgainstWall(Ship ship, int turn) {
		if (ship.pos.y == 0 && ship.orientation == 1) return "STARBOARD";
		if (ship.pos.y == 0 && ship.orientation == 2) return "PORT";

		if (ship.pos.y == MAP_HEIGHT - 1 && ship.orientation == 4) return "STARBOARD";
		if (ship.pos.y == MAP_HEIGHT - 1 && ship.orientation == 5) return "PORT";

		if (ship.pos.x == 0 && ship.orientation == 2) return "STARBOARD";
		if (ship.pos.x == 0 && ship.orientation == 4) return "PORT";

		if (ship.pos.x == MAP_WIDTH - 1 && ship.orientation == 5) return "STARBOARD";
		if (ship.pos.x == MAP_WIDTH - 1 && ship.orientation == 1) return "PORT";

		return (turn % 2 == 0) ? "PORT" : "STARBOARD";
	}

	/*****************************************************************
	 * Utils
	 *****************************************************************/
	public static int abs(int a) {
		return a < 0 ? -a : a;
	}

	public static int min(int a, int b) {
		return a < b ? a : b;
	}

	public static int max(int a, int b) {
		return a > b ? a : b;
	}

	/*****************************************************************
	 * Data model
	 *****************************************************************/
	public static class Entity {
		Coord pos;
		int id;

		public Entity(int id, int x, int y) {
			this.id = id;
			this.pos = new Coord(x, y);
		}
	}

	public static class Ship extends Entity {
		int orientation;
		int speed;
		int rum;
		int owner;
		Coord nextPos;

		public Ship(int id, int x, int y, int dir, int speed, int rum, int owner) {
			super(id, x, y);
			this.orientation = dir;
			this.speed = speed;
			this.rum = rum;
			this.owner = owner;
			this.nextPos = this.pos.neighbor(this.orientation);
		}

		@Override
		public String toString() {
			String s = "Ship " + id + " at " + pos + " dir=" + orientation + " speed=" + +speed + " rum=" + rum;
			if (!this.nextPos.isInsideMap()) s += " <next pos outside>";
			return s;
		}
	}

	public static class Barrel extends Entity {
		int rum;

		public Barrel(int id, int x, int y, int rum) {
			super(id, x, y);
			this.rum = rum;
		}

		@Override
		public String toString() {
			return "Barrel " + id + " at " + pos + " rum=" + rum;
		}
	}

	public static class Mine extends Entity {
		public Mine(int id, int x, int y) {
			super(id, x, y);
		}

		@Override
		public String toString() {
			return "Mine " + id + " at " + pos;
		}
	}

	public static class CannonBall extends Entity {
		int owner;
		int tbi; // turn before impact

		public CannonBall(int id, int x, int y, int owner, int tbi) {
			super(id, x, y);
			this.owner = owner;
			this.tbi = tbi;
		}

		@Override
		public String toString() {
			return "CannonBall " + id + " at " + pos + " by=" + owner + " impact in " + tbi;
		}
	}

	/*****************************************************************
	 * Tooling from Referee
	 *****************************************************************/
	private static final int MAP_WIDTH = 23;
	private static final int MAP_HEIGHT = 21;

	public static class Coord {
		private final static int[][] DIRECTIONS_EVEN = new int[][] { { 1, 0 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 }, { 0, 1 } };
		private final static int[][] DIRECTIONS_ODD = new int[][] { { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 1, 1 } };
		private final int x;
		private final int y;

		public Coord(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public Coord(Coord other) {
			this.x = other.x;
			this.y = other.y;
		}

		public double angle(Coord targetPosition) {
			double dy = (targetPosition.y - this.y) * Math.sqrt(3) / 2;
			double dx = targetPosition.x - this.x + ((this.y - targetPosition.y) & 1) * 0.5;
			double angle = -Math.atan2(dy, dx) * 3 / Math.PI;
			if (angle < 0) {
				angle += 6;
			} else if (angle >= 6) {
				angle -= 6;
			}
			return angle;
		}

		CubeCoordinate toCubeCoordinate() {
			int xp = x - (y - (y & 1)) / 2;
			int zp = y;
			int yp = -(xp + zp);
			return new CubeCoordinate(xp, yp, zp);
		}

		Coord neighbor(int orientation) {
			int newY, newX;
			if (this.y % 2 == 1) {
				newY = this.y + DIRECTIONS_ODD[orientation][1];
				newX = this.x + DIRECTIONS_ODD[orientation][0];
			} else {
				newY = this.y + DIRECTIONS_EVEN[orientation][1];
				newX = this.x + DIRECTIONS_EVEN[orientation][0];
			}

			return new Coord(newX, newY);
		}

		boolean isInsideMap() {
			return x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT;
		}

		int distanceTo(Coord dst) {
			return this.toCubeCoordinate().distanceTo(dst.toCubeCoordinate());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || getClass() != obj.getClass()) { return false; }
			Coord other = (Coord) obj;
			return y == other.y && x == other.x;
		}

		@Override
		public String toString() {
			return x + " " + y;
		}
	}

	public static class CubeCoordinate {
		static int[][] directions = new int[][] { { 1, -1, 0 }, { +1, 0, -1 }, { 0, +1, -1 }, { -1, +1, 0 }, { -1, 0, +1 }, { 0, -1, +1 } };
		int x, y, z;

		public CubeCoordinate(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		Coord toOffsetCoordinate() {
			int newX = x + (z - (z & 1)) / 2;
			int newY = z;
			return new Coord(newX, newY);
		}

		CubeCoordinate neighbor(int orientation) {
			int nx = this.x + directions[orientation][0];
			int ny = this.y + directions[orientation][1];
			int nz = this.z + directions[orientation][2];

			return new CubeCoordinate(nx, ny, nz);
		}

		int distanceTo(CubeCoordinate dst) {
			return (Math.abs(x - dst.x) + Math.abs(y - dst.y) + Math.abs(z - dst.z)) / 2;
		}

		@Override
		public String toString() {
			return x + " " + y + " " + z;
		}
	}

}