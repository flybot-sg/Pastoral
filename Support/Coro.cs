// Copyright (c) 2018 Flybot Pte Ltd, Singapore.
//
// This file is distributed under the Eclipse Public License, the same as
// Clojure.
using clojure.lang;
using System.Collections.Generic;
using System;
using UnityEngine;
using System.Linq;

namespace Pastoral
{
	public class CoroRoot : MonoBehaviour
	{
		static Keyword DefaultKey = RT.keyword("coroutine", "default");

		public Dictionary<object, IFn> activeUpdateCoroutines = new Dictionary<object, IFn>();

		// TODO update only for now, build out other messages
		void Update()
		{
			InvokeAllCoroutines(activeUpdateCoroutines);
		}

		void InvokeAllCoroutines(Dictionary<object, IFn> dict)
		{
			var symsToRemove = new List<object>();
			foreach (var sym in dict.Keys)
			{
				var coro = dict[sym];
				try
				{
					coro.invoke();
				}
				catch (Exception e)
				{
					Debug.LogException(e);
					symsToRemove.Add(sym);
				}

				if (!((Coroutine)coro).HasMore)
				{
					symsToRemove.Add(sym);
				}
			}

			foreach (var sym in symsToRemove)
			{
				dict.Remove(sym);
			}
		}

		public void Begin(IFn newCoro, object key)
		{
			activeUpdateCoroutines[key] = newCoro;
		}

		public void Begin(IFn newCoro)
		{
			Begin(newCoro, DefaultKey);
		}

		public void Stop(object key)
		{
			if (activeUpdateCoroutines.ContainsKey(key))
			{
				activeUpdateCoroutines.Remove(key);
			}
		}

		public void StopAll()
		{
			activeUpdateCoroutines.Clear();
		}
	}

	public interface Coroutine
	{
		bool HasMore { get; }
	}

	public class CoroSingle : AFn, Coroutine
	{
		public IFn fn;
		public bool HasMore { get; private set; }
		public IFn Fn { get { return fn; } }

		public CoroSingle(IFn fn)
		{
			this.fn = fn;
			HasMore = true;
		}

		public override object invoke()
		{
			if (HasMore)
			{
				var val = fn.invoke();
				HasMore = RT.booleanCast(val);
			}

			return HasMore;
		}

		public override bool HasArity(int arity)
		{
			return arity == 0;
		}
	}

	public class CoroArray : AFn, Coroutine
	{
		IFn[] fns;
		int index = 0;

		public bool HasMore { get; private set; }
		public IFn[] Fns { get { return fns; } }
		public int Index { get { return index; } }

		public CoroArray(IFn[] fns)
		{
			this.fns = fns;
			HasMore = true;
		}

		public override object invoke()
		{
			if (HasMore)
			{
				var current = fns[index];
				var val = current.invoke();
				var fnHasMore = RT.booleanCast(val);
				if (!fnHasMore)
				{
					index++;
					HasMore = index < fns.Length;
				}
			}

			return HasMore;
		}

		public override bool HasArity(int arity)
		{
			return arity == 0;
		}
	}

	public class CoroTogether : AFn, Coroutine
	{
		public IFn[] fns;

		public bool HasMore { get; private set; }
		public IFn[] Fns { get { return fns; } }

		public CoroTogether(IFn[] fns)
		{
			this.fns = fns.Select(f => typeof(Coroutine).IsAssignableFrom(f.GetType()) ? f : new CoroSingle(f)).ToArray();
			HasMore = true;
		}

		public override object invoke()
		{
			if (HasMore)
			{
				bool hasMore = false;
				for (int i = 0; i < fns.Length; i++)
				{
					var current = fns[i];
					if (((Coroutine)current).HasMore)
						hasMore = RT.booleanCast(current.invoke()) || hasMore;
				}
				HasMore = hasMore;
			}

			return HasMore;
		}

		public override bool HasArity(int arity)
		{
			return arity == 0;
		}
	}

	public class CoroSequence : AFn, Coroutine
	{
		ISeq fns;
		public bool HasMore { get; private set; }
		public ISeq Fns { get { return fns; } }

		public CoroSequence(ISeq fns)
		{
			this.fns = fns;
			HasMore = true;
		}

		public override object invoke()
		{
			if (HasMore)
			{
				var val = ((IFn)fns.first()).invoke();
				if (RT.booleanCast(val) == false)
					fns = fns.next();
				HasMore = fns != null;
			}

			return HasMore;
		}

		public override bool HasArity(int arity)
		{
			return arity == 0;
		}
	}

	public class CoroGenerate : AFn, Coroutine
	{
		public IFn fn;
		public IFn generatedFn;
		public bool HasMore { get; private set; }
		public IFn Fn { get { return fn; } }

		public CoroGenerate(IFn fn)
		{
			this.fn = fn;
			HasMore = true;
		}

		void EnsureGeneratedFn()
		{
			if (generatedFn == null)
			{
				generatedFn = (IFn)fn.invoke();
			}
		}

		public override object invoke()
		{
			EnsureGeneratedFn();

			if (HasMore)
			{
				var val = generatedFn.invoke();
				HasMore = RT.booleanCast(val);
			}

			return HasMore;
		}

		public override bool HasArity(int arity)
		{
			return arity == 0;
		}
	}
}