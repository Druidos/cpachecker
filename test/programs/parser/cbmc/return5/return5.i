# 1 "return5/main.c"
# 1 "<eingebaut>"
# 1 "<Kommandozeile>"
# 1 "return5/main.c"
short f0(short x)
{
  int z;

  do {
    z=0;
    if(x <= 0) { z=1;
      return 100; }
  }
  while( x-- );

  z=2;
  return 200;
}

short f1(short x)
{
  do {
    if(x <= 0)
      return 100;
  }
  while( --x );

  return 200;
}

int main()
{
  int flag;
  short a;
  short res0, res1;

  if( flag )
    a = 1;
  else
    a = 1;

  res0 = f0(a);
  res1 = f1(a);

  assert(res0 == res1);

  return 0;
}
