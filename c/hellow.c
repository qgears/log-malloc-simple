// Compile: gcc -rdynamic hellow.c
#include <stdio.h>
#include <stdlib.h>
void recurseDepth(int n)
{
	if(n<1)
	{
		malloc(3456);
		return;
	}else
	{
		recurseDepth(n-1);
	}
}

int main ()
{
	printf("Hello World!\n");
	void * p=malloc(111);
	free(p);
	for(int i=0;i<20;++i)
	{
		recurseDepth(i);
	}
}
